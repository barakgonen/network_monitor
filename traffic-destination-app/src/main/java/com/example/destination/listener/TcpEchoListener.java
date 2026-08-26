package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpEchoListener implements Listener {

    private final InterfaceEntry config;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();
    private volatile boolean running;

    public TcpEchoListener(InterfaceEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getPort());
        running = true;
        acceptThread = new Thread(this::acceptLoop, "destination-tcp-accept-" + config.getKey());
        acceptThread.setDaemon(true);
        acceptThread.start();
        System.out.println("[" + config.getKey() + "] TCP destination listening on port " + config.getPort()
                + " (replyMode=" + config.getReplyMode() + ")");
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
        connectionExecutor.shutdownNow();
    }
}
