package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.mirror.TcpMirrorConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bidirectional TCP relay, in one of two modes selected by {@link RelayEntry#getListenMode()}/
 * {@link RelayEntry#getDestinationMode()}:
 *
 * <ul>
 *   <li><b>Forward mode</b> (default, {@code listenMode=SERVER}, {@code destinationMode=CLIENT}):
 *   the relay binds {@code listen} and accepts producer connections in; for each one it opens an
 *   outbound connection to {@code destination}. Matches a producer that's a TCP client and a
 *   destination that's a TCP server.</li>
 *   <li><b>Reverse mode</b> ({@code listenMode=CLIENT}, {@code destinationMode=SERVER}): the
 *   relay instead connects out to {@code listen} (the producer's own listening server address,
 *   via a background reconnect loop) and binds {@code destination}, accepting the destination
 *   side's connection in. Matches a producer that's itself a TCP server and a destination that's
 *   a TCP client - the roles fully invert, but splicing/mirroring behave identically once both
 *   sides are connected. Only one paired session is maintained at a time ("last one wins" on
 *   either side reconnecting), consistent with this project's single-producer-per-interface
 *   demo scale.</li>
 * </ul>
 *
 * Both directions are additionally duplicated onto a single persistent mirror connection shared
 * across every session this relay handles - see {@link TcpMirrorConnection} for why a persistent
 * connection (rather than one per chunk/session) is required to preserve the monitor's
 * header+bodyLength stream framing.
 */
public class TcpRelay implements Relay {

    private static final int BUFFER_SIZE = 8192;
    private static final long COORDINATOR_POLL_MILLIS = 50L;
    private static final long CLOSED_POLL_MILLIS = 50L;

    private final RelayEntry config;
    private final boolean reverseMode;
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

    private TcpMirrorConnection mirrorConnection;
    private volatile boolean running;

    // Forward mode only.
    private ServerSocket serverSocket;
    private Thread acceptThread;

    // Reverse mode only.
    private ServerSocket destinationServerSocket;
    private Thread destinationAcceptThread;
    private Thread producerConnectLoopThread;
    private Thread coordinatorThread;
    private final AtomicReference<Socket> reverseProducerSocket = new AtomicReference<>();
    private final AtomicReference<Socket> reverseDestinationSocket = new AtomicReference<>();

    public TcpRelay(RelayEntry config) {
        this.config = config;
        this.reverseMode = "CLIENT".equalsIgnoreCase(config.getListenMode())
                && "SERVER".equalsIgnoreCase(config.getDestinationMode());
    }

    @Override
    public void start() throws IOException {
        mirrorConnection = new TcpMirrorConnection(config.getMirror().getHost(), config.getMirror().getPort(), config.getKey());
        running = true;

        if (reverseMode) {
            startReverseMode();
        } else {
            startForwardMode();
        }
    }

    private void startForwardMode() throws IOException {
        EndpointConfig listen = config.getListen();
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(listen.getHost(), listen.getPort()));

        acceptThread = new Thread(this::acceptLoop, "proxy-tcp-accept-" + config.getKey());
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("[" + config.getKey() + "] TCP relay listening on " + listen.getHost() + ":" + listen.getPort()
                + " -> destination " + config.getDestination().getHost() + ":" + config.getDestination().getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")");
    }

    private void startReverseMode() throws IOException {
        EndpointConfig destination = config.getDestination();
        destinationServerSocket = new ServerSocket();
        destinationServerSocket.bind(new InetSocketAddress(destination.getHost(), destination.getPort()));

        destinationAcceptThread = new Thread(this::destinationAcceptLoop, "proxy-tcp-dest-accept-" + config.getKey());
        destinationAcceptThread.setDaemon(true);
        destinationAcceptThread.start();

        producerConnectLoopThread = new Thread(this::producerConnectLoop, "proxy-tcp-producer-connect-" + config.getKey());
        producerConnectLoopThread.setDaemon(true);
        producerConnectLoopThread.start();

        coordinatorThread = new Thread(this::coordinatorLoop, "proxy-tcp-coordinator-" + config.getKey());
        coordinatorThread.setDaemon(true);
        coordinatorThread.start();

        EndpointConfig listen = config.getListen();
        System.out.println("[" + config.getKey() + "] TCP relay (reverse mode) connecting to producer server "
                + listen.getHost() + ":" + listen.getPort() + " <- accepting destination on "
                + destination.getHost() + ":" + destination.getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")");
    }

    // ---- Forward mode ----

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            Socket clientSocket;
            try {
                clientSocket = serverSocket.accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] TCP relay accept error: " + e.getMessage());
                }
                continue;
            }
            connectionExecutor.submit(() -> handleForwardConnection(clientSocket));
        }
    }

    private void handleForwardConnection(Socket clientSocket) {
        activeSockets.add(clientSocket);

        EndpointConfig destination = config.getDestination();
        Socket destSocket;
        try {
            destSocket = new Socket();
            destSocket.connect(new InetSocketAddress(destination.getHost(), destination.getPort()),
                    (int) config.getDestinationConnectTimeoutMillis());
        } catch (IOException e) {
            System.err.println("[" + config.getKey() + "] TCP relay could not reach destination "
                    + destination.getHost() + ":" + destination.getPort() + ": " + e.getMessage());
            closeQuietly(clientSocket);
            activeSockets.remove(clientSocket);
            return;
        }
        activeSockets.add(destSocket);

        spliceAndClose(clientSocket, destSocket);
    }

    // ---- Reverse mode ----

    private void producerConnectLoop() {
        EndpointConfig listen = config.getListen();
        while (running) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(listen.getHost(), listen.getPort()),
                        (int) config.getListenConnectTimeoutMillis());
                activeSockets.add(socket);
                reverseProducerSocket.set(socket);
                System.out.println("[" + config.getKey() + "] connected to producer server "
                        + listen.getHost() + ":" + listen.getPort());
                waitUntilClosed(socket);
                reverseProducerSocket.compareAndSet(socket, null);
                activeSockets.remove(socket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[" + config.getKey() + "] failed to connect to producer server: " + e.getMessage());
                }
            }
            sleepQuietly(config.getListenReconnectDelayMillis());
        }
    }

    private void destinationAcceptLoop() {
        while (running && !destinationServerSocket.isClosed()) {
            Socket socket;
            try {
                socket = destinationServerSocket.accept();
            } catch (IOException e) {
                if (!destinationServerSocket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] TCP relay destination accept error: " + e.getMessage());
                }
                continue;
            }
            activeSockets.add(socket);
            System.out.println("[" + config.getKey() + "] destination connected from " + socket.getRemoteSocketAddress());

            Socket previous = reverseDestinationSocket.getAndSet(socket);
            if (previous != null) {
                closeQuietly(previous);
                activeSockets.remove(previous);
            }
        }
    }

    private void coordinatorLoop() {
        while (running) {
            Socket producer = reverseProducerSocket.get();
            Socket destination = reverseDestinationSocket.get();
            if (producer != null && !producer.isClosed() && destination != null && !destination.isClosed()) {
                spliceAndClose(producer, destination);
                reverseProducerSocket.compareAndSet(producer, null);
                reverseDestinationSocket.compareAndSet(destination, null);
            } else {
                sleepQuietly(COORDINATOR_POLL_MILLIS);
            }
        }
    }

    /**
     * Polls the local {@code isClosed()} flag, which only flips once something actually reads
     * from (or writes to) this socket and observes the peer's FIN/RST - in practice that's
     * {@link #splice}, once {@link #coordinatorLoop} has paired this connection with a
     * destination one. A producer that disconnects before ever being paired (no destination
     * connected yet) won't be noticed here and won't trigger a reconnect until something reads
     * from the socket; acceptable at this project's demo scale where both sides are expected up
     * around the same time, but a real robustness gap if that assumption doesn't hold.
     */
    private void waitUntilClosed(Socket socket) {
        while (running && !socket.isClosed()) {
            sleepQuietly(CLOSED_POLL_MILLIS);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- Shared splice/mirror logic ----

    private void spliceAndClose(Socket a, Socket b) {
        Thread aToB = new Thread(() -> splice(a, b), "proxy-tcp-a2b-" + config.getKey());
        Thread bToA = new Thread(() -> splice(b, a), "proxy-tcp-b2a-" + config.getKey());
        aToB.setDaemon(true);
        bToA.setDaemon(true);
        aToB.start();
        bToA.start();

        joinQuietly(aToB);
        joinQuietly(bToA);

        closeQuietly(a);
        closeQuietly(b);
        activeSockets.remove(a);
        activeSockets.remove(b);
    }

    private void splice(Socket from, Socket to) {
        byte[] buffer = new byte[BUFFER_SIZE];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                out.flush();
                mirrorConnection.write(buffer, read);
            }
        } catch (IOException e) {
            // Normal on connection close from either side.
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void joinQuietly(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (acceptThread != null) {
            acceptThread.interrupt();
        }
        if (destinationServerSocket != null) {
            try {
                destinationServerSocket.close();
            } catch (IOException ignored) {
            }
        }
        if (destinationAcceptThread != null) {
            destinationAcceptThread.interrupt();
        }
        if (producerConnectLoopThread != null) {
            producerConnectLoopThread.interrupt();
        }
        if (coordinatorThread != null) {
            coordinatorThread.interrupt();
        }
        for (Socket socket : activeSockets) {
            closeQuietly(socket);
        }
        connectionExecutor.shutdownNow();
        if (mirrorConnection != null) {
            mirrorConnection.close();
        }
    }
}
