package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * TCP echo listener, in one of two modes selected by {@link InterfaceEntry#getMode()}:
 * "SERVER" (default) binds {@code port} and accepts connections in; "CLIENT" instead connects
 * out to {@code host}:{@code port} via a background reconnect loop - needed when the peer this
 * interface talks to (e.g. traffic-proxy-app's TcpRelay in reverse mode) is itself listening
 * rather than connecting in.
 */
public class TcpEchoListener implements Listener {

    private static final long CONNECT_TIMEOUT_MILLIS = 3_000L;
    private static final long RECONNECT_DELAY_MILLIS = 2_000L;

    private final InterfaceEntry config;
    private final boolean clientMode;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private Thread connectLoopThread;
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private volatile boolean running;

    public TcpEchoListener(InterfaceEntry config) {
        this.config = config;
        this.clientMode = "CLIENT".equalsIgnoreCase(config.getMode());
    }

    @Override
    public void start() throws IOException {
        running = true;
        if (clientMode) {
            connectLoopThread = new Thread(this::connectLoop, "destination-tcp-connect-" + config.getKey());
            connectLoopThread.setDaemon(true);
            connectLoopThread.start();
            System.out.println("[" + config.getKey() + "] TCP destination connecting to "
                    + config.getHost() + ":" + config.getPort() + " (replyMode=" + config.getReplyMode() + ")");
        } else {
            serverSocket = new ServerSocket(config.getPort());
            acceptThread = new Thread(this::acceptLoop, "destination-tcp-accept-" + config.getKey());
            acceptThread.setDaemon(true);
            acceptThread.start();
            System.out.println("[" + config.getKey() + "] TCP destination listening on port " + config.getPort()
                    + " (replyMode=" + config.getReplyMode() + ")");
        }
    }

    private void connectLoop() {
        while (running) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(config.getHost(), config.getPort()), (int) CONNECT_TIMEOUT_MILLIS);
                System.out.println("[" + config.getKey() + "] connected to " + config.getHost() + ":" + config.getPort());
                handleConnection(socket);
            } catch (IOException e) {
                if (running) {
                    System.err.println("[" + config.getKey() + "] TCP destination connect failed: " + e.getMessage());
                }
            }
            sleepQuietly(RECONNECT_DELAY_MILLIS);
        }
    }

    private void acceptLoop() {
        while (running && !serverSocket.isClosed()) {
            Socket connection;
            try {
                connection = serverSocket.accept();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] TCP accept error: " + e.getMessage());
                }
                continue;
            }
            connectionExecutor.submit(() -> handleConnection(connection));
        }
    }

    private void handleConnection(Socket connection) {
        try (connection; InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                System.out.println("[" + config.getKey() + "] received " + read + " bytes from "
                        + connection.getRemoteSocketAddress());
                if (config.getReplyMode() == ReplyMode.ECHO) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("[" + config.getKey() + "] TCP connection error: " + e.getMessage());
        }
    }

    private void sleepQuietly(long millis) {
        if (!running) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
        if (connectLoopThread != null) {
            connectLoopThread.interrupt();
        }
        connectionExecutor.shutdownNow();
    }
}
