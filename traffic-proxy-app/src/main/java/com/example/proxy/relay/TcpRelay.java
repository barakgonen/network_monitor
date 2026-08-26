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

/**
 * Accepts a producer connection, opens one outbound connection to {@code destination}, and
 * splices bytes bidirectionally. Both directions are additionally duplicated onto a single
 * persistent mirror connection shared across every client connection this relay handles - see
 * {@link TcpMirrorConnection} for why a persistent connection (rather than one per chunk/client)
 * is required to preserve the monitor's header+bodyLength stream framing.
 */
public class TcpRelay implements Relay {

    private static final int BUFFER_SIZE = 8192;

    private final RelayEntry config;
    private final Set<Socket> activeSockets = ConcurrentHashMap.newKeySet();
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private TcpMirrorConnection mirrorConnection;
    private volatile boolean running;

    public TcpRelay(RelayEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        EndpointConfig listen = config.getListen();
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress(listen.getHost(), listen.getPort()));
        mirrorConnection = new TcpMirrorConnection(config.getMirror().getHost(), config.getMirror().getPort(), config.getKey());
        running = true;

        acceptThread = new Thread(this::acceptLoop, "proxy-tcp-accept-" + config.getKey());
        acceptThread.setDaemon(true);
        acceptThread.start();

        System.out.println("[" + config.getKey() + "] TCP relay listening on " + listen.getHost() + ":" + listen.getPort()
                + " -> destination " + config.getDestination().getHost() + ":" + config.getDestination().getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")");
    }

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
            connectionExecutor.submit(() -> handleConnection(clientSocket));
        }
    }

    private void handleConnection(Socket clientSocket) {
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

        Thread clientToDest = new Thread(() -> splice(clientSocket, destSocket), "proxy-tcp-c2d-" + config.getKey());
        Thread destToClient = new Thread(() -> splice(destSocket, clientSocket), "proxy-tcp-d2c-" + config.getKey());
        clientToDest.setDaemon(true);
        destToClient.setDaemon(true);
        clientToDest.start();
        destToClient.start();

        joinQuietly(clientToDest);
        joinQuietly(destToClient);

        closeQuietly(clientSocket);
        closeQuietly(destSocket);
        activeSockets.remove(clientSocket);
        activeSockets.remove(destSocket);
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
        for (Socket socket : activeSockets) {
            closeQuietly(socket);
        }
        connectionExecutor.shutdownNow();
        if (mirrorConnection != null) {
            mirrorConnection.close();
        }
    }
}
