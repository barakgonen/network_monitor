package com.example.monitor.publishing;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Component
public class UdpMessagePublisher {
    public void send(String host, int port, byte[] payload) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send UDP packet to " + host + ":" + port, e);
        }
    }
}
