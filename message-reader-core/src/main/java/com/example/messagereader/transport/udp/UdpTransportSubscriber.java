package com.example.messagereader.transport.udp;

import com.example.messagereader.api.MessageReaderException;
import com.example.messagereader.api.RawTrafficPacket;
import com.example.messagereader.api.TransportProtocol;
import com.example.messagereader.transport.RawPacketHandler;
import com.example.messagereader.transport.TransportSubscriber;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class UdpTransportSubscriber implements TransportSubscriber {
    private final int localPort;
    private final int bufferSizeBytes;
    private final RawPacketHandler packetHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private DatagramSocket socket;
    private Thread worker;

    public UdpTransportSubscriber(int localPort, int bufferSizeBytes, RawPacketHandler packetHandler) {
        this.localPort = localPort;
        this.bufferSizeBytes = bufferSizeBytes;
        this.packetHandler = packetHandler;
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        worker = new Thread(this::listen, "udp-traffic-reader-" + localPort);
        worker.setDaemon(true);
        worker.start();
    }

    private void listen() {
        try (DatagramSocket datagramSocket = new DatagramSocket(localPort)) {
            this.socket = datagramSocket;

            while (running.get()) {
                byte[] buffer = new byte[bufferSizeBytes];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                datagramSocket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());

                packetHandler.onPacket(new RawTrafficPacket(
                        TransportProtocol.UDP,
                        localPort,
                        packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                        payload,
                        Instant.now()
                ));
            }
        } catch (Exception e) {
            if (running.get()) {
                throw new MessageReaderException("UDP transport subscriber failed on port " + localPort, e);
            }
        } finally {
            running.set(false);
            socket = null;
        }
    }

    @Override
    public void stop() {
        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
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
