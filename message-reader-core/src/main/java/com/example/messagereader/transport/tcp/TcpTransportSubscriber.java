package com.example.messagereader.transport.tcp;

import com.example.messagereader.api.MessageReaderException;
import com.example.messagereader.api.RawTrafficPacket;
import com.example.messagereader.api.TransportProtocol;
import com.example.messagereader.transport.RawPacketHandler;
import com.example.messagereader.transport.TransportSubscriber;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TCP V1 subscriber.
 *
 * Note: This is a raw bytes-per-read subscriber. Real TCP protocols should add framing later.
 */
public class TcpTransportSubscriber implements TransportSubscriber {
    private final int localPort;
    private final int bufferSizeBytes;
    private final RawPacketHandler packetHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread worker;

    public TcpTransportSubscriber(int localPort, int bufferSizeBytes, RawPacketHandler packetHandler) {
        this.localPort = localPort;
        this.bufferSizeBytes = bufferSizeBytes;
        this.packetHandler = packetHandler;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        worker = new Thread(this::listen, "tcp-traffic-reader-" + localPort);
        worker.setDaemon(true);
        worker.start();
    }

    private void listen() {
        try (ServerSocket server = new ServerSocket(localPort)) {
            this.serverSocket = server;

            while (running.get()) {
                Socket socket = server.accept();
                handleClient(socket);
            }
        } catch (Exception e) {
            if (running.get()) {
                throw new MessageReaderException("TCP transport subscriber failed on port " + localPort, e);
            }
        } finally {
            running.set(false);
            serverSocket = null;
        }
    }

    private void handleClient(Socket socket) {
        try (socket; InputStream inputStream = socket.getInputStream()) {
            byte[] buffer = new byte[bufferSizeBytes];
            int read;

            while (running.get() && (read = inputStream.read(buffer)) != -1) {
                byte[] payload = Arrays.copyOf(buffer, read);

                packetHandler.onPacket(new RawTrafficPacket(
                        TransportProtocol.TCP,
                        localPort,
                        socket.getRemoteSocketAddress().toString(),
                        payload,
                        Instant.now()
                ));
            }
        } catch (Exception e) {
            if (running.get()) {
                throw new MessageReaderException("Failed handling TCP client on port " + localPort, e);
            }
        }
    }

    @Override
    public void stop() {
        running.set(false);

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception ignored) {
        }

        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }
}
