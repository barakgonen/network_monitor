package com.example.monitor.publisher;

import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Component
public class UdpPublisher {
    public void send(String host, int port, byte[] payload) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        } catch (Exception e) {
            throw new IllegalStateException("Failed sending UDP packet to " + host + ":" + port, e);
        }
    }
}
