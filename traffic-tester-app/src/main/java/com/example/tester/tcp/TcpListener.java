package com.example.tester.tcp;

import com.example.tester.decode.KnownMessageDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP server-mode counterpart to {@code UdpListener}: binds and accepts connections in, and for
 * each one, immediately pushes the configured payload (the tester acting as a TCP server that
 * produces its own message, rather than a client dialing out) then reads and decodes/logs
 * whatever comes back on that same connection - needed when the peer this interface talks to
 * (e.g. traffic-proxy-app's TcpRelay in reverse mode) is itself a client connecting in.
 */
public class TcpListener implements AutoCloseable {

    private final int port;
    private final byte[] payload;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ExecutorService connectionExecutor = Executors.newCachedThreadPool();

    private ServerSocket serverSocket;
    private Thread acceptThread;

    public TcpListener(int port, byte[] payload) {
        this.port = port;
        this.payload = payload;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        serverSocket = new ServerSocket(port);
        System.out.println("Tester TCP server listener started on port " + port);

        acceptThread = new Thread(this::acceptLoop, "tester-tcp-listener-" + port);
        acceptThread.setDaemon(false);
        acceptThread.start();
    }

    public void await(Duration duration) throws InterruptedException {
        Instant deadline = Instant.now().plus(duration);

        while (running.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }

        close();
    }

    private void acceptLoop() {
        while (running.get() && !serverSocket.isClosed()) {
            Socket connection;
            try {
                connection = serverSocket.accept();
            } catch (IOException e) {
                if (running.get() && !serverSocket.isClosed()) {
                    System.err.println("Tester TCP listener accept error: " + e.getMessage());
                }
                continue;
            }
            connectionExecutor.submit(() -> handleConnection(connection));
        }
    }

    private void handleConnection(Socket connection) {
        try (connection) {
            OutputStream out = connection.getOutputStream();
            out.write(payload);
            out.flush();
            System.out.println("Tester TCP server sent " + payload.length + " bytes to "
                    + connection.getRemoteSocketAddress() + ", hex=" + HexFormat.of().formatHex(payload));

            InputStream in = connection.getInputStream();
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) != -1) {
                byte[] received = Arrays.copyOf(buffer, read);

                System.out.println();
                System.out.println("=== TCP MESSAGE ARRIVED TO TESTER ===");
                System.out.println("From: " + connection.getRemoteSocketAddress());
                System.out.println("Local port: " + port);
                System.out.println("Bytes: " + received.length);
                System.out.println("Hex: " + HexFormat.of().formatHex(received));
                System.out.println("Text: " + new String(received, StandardCharsets.UTF_8));

                KnownMessageDecoder.tryDecodeAndPrint(received);

                System.out.println("=====================================");
                System.out.println();
            }
        } catch (IOException e) {
            System.err.println("Tester TCP listener connection error: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running.set(false);

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }

        if (acceptThread != null) {
            acceptThread.interrupt();
        }

        connectionExecutor.shutdownNow();

        System.out.println("Tester TCP listener stopped");
    }
}
