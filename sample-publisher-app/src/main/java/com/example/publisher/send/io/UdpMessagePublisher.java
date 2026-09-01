package com.example.publisher.send.io;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Copied from {@code com.example.monitor.publishing.UdpMessagePublisher} (traffic-monitor-app-core)
 * rather than depended on, so this app has zero dependency on traffic-monitor-app/-core - see
 * CLAUDE.md's "Publishing lives in sample-publisher-app, not here". Metrics counters were dropped
 * in the copy: this app exposes no {@code /actuator/prometheus} endpoint, so they were never
 * observable here.
 */
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
