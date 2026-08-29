package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.reply.PongReplyEncoder;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;

public class UdpEchoListener implements Listener {

    private final InterfaceEntry config;
    private DatagramSocket socket;
    private Thread receiveThread;
    private volatile boolean running;

    public UdpEchoListener(InterfaceEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws SocketException {
        socket = new DatagramSocket(config.getPort());
        running = true;
        receiveThread = new Thread(this::receiveLoop, "destination-udp-" + config.getKey());
        receiveThread.setDaemon(true);
        receiveThread.start();
        System.out.println("[" + config.getKey() + "] UDP destination listening on port " + config.getPort()
                + " (replyMode=" + config.getReplyMode() + ")");
    }

    private void receiveLoop() {
        byte[] buffer = new byte[65507];
        while (running && !socket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                socket.receive(packet);
            } catch (Exception e) {
                if (!socket.isClosed()) {
                    System.err.println("[" + config.getKey() + "] UDP receive error: " + e.getMessage());
                }
                continue;
            }

            byte[] received = new byte[packet.getLength()];
            System.arraycopy(packet.getData(), packet.getOffset(), received, 0, packet.getLength());
            System.out.println("[" + config.getKey() + "] received " + received.length + " bytes from "
                    + packet.getAddress() + ":" + packet.getPort());

            if (config.getReplyMode() == ReplyMode.ECHO) {
                sendReply(packet, received);
            } else if (config.getReplyMode() == ReplyMode.PONG) {
                try {
                    sendReply(packet, PongReplyEncoder.buildPongReply(received));
                } catch (Exception e) {
                    System.err.println("[" + config.getKey() + "] failed to build Pong reply: " + e.getMessage());
                }
            }
        }
    }

    private void sendReply(DatagramPacket originalPacket, byte[] replyBytes) {
        int targetPort = config.getReplyPort() != null ? config.getReplyPort() : originalPacket.getPort();
        try {
            socket.send(new DatagramPacket(replyBytes, replyBytes.length,
                    originalPacket.getAddress(), targetPort));
        } catch (Exception e) {
            System.err.println("[" + config.getKey() + "] UDP reply send failed: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        running = false;
        if (socket != null) {
            socket.close();
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
    }
}
