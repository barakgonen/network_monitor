package com.example.tester.udp;

import com.example.tester.decode.KnownMessageDecoder;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpListener implements AutoCloseable {

    private final int port;
    private final int bufferSizeBytes;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread listenerThread;

    public UdpListener(int port, int bufferSizeBytes) {
        this.port = port;
        this.bufferSizeBytes = bufferSizeBytes;
    }

    public void start() throws java.net.SocketException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        // Bind synchronously (not inside the background thread) so socket() is guaranteed ready
        // the moment start() returns - a caller that wants to send from this same socket (so a
        // reply routed back to this port actually arrives here) must not race the listener thread.
        socket = new DatagramSocket(port);
        System.out.println("Tester UDP listener started on port " + port);

        listenerThread = new Thread(this::listen, "tester-udp-listener-" + port);
        listenerThread.setDaemon(false);
        listenerThread.start();
    }

    /** The socket this listener is bound to, for reuse as a shared send socket. */
    public DatagramSocket socket() {
        return socket;
    }

    public void await(Duration duration) throws InterruptedException {
        Instant deadline = Instant.now().plus(duration);

        while (running.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }

        close();
    }

    private void listen() {
        try {
            while (running.get()) {
                byte[] buffer = new byte[bufferSizeBytes];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());

                System.out.println();
                System.out.println("=== UDP MESSAGE ARRIVED TO TESTER ===");
                System.out.println("From: " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                System.out.println("Local port: " + port);
                System.out.println("Bytes: " + payload.length);
                System.out.println("Hex: " + HexFormat.of().formatHex(payload));
                System.out.println("Text: " + new String(payload, StandardCharsets.UTF_8));

                KnownMessageDecoder.tryDecodeAndPrint(payload);

                System.out.println("=====================================");
                System.out.println();
            }
        } catch (Exception e) {
            if (running.get()) {
                System.err.println("Tester UDP listener failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }
    }

    @Override
    public void close() {
        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        System.out.println("Tester UDP listener stopped");
    }
}
