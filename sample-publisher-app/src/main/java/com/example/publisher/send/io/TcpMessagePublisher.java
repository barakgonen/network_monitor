package com.example.publisher.send.io;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Socket;

/**
 * Copied from {@code com.example.monitor.publishing.TcpMessagePublisher} (traffic-monitor-app-core)
 * rather than depended on, so this app has zero dependency on traffic-monitor-app/-core - see
 * CLAUDE.md's "Publishing lives in sample-publisher-app, not here". Metrics counters were dropped
 * in the copy: this app exposes no {@code /actuator/prometheus} endpoint, so they were never
 * observable here.
 */
@Component
public class TcpMessagePublisher {

    public void send(String host, int port, byte[] payload) {
        try (Socket socket = new Socket(host, port)) {
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send TCP message to " + host + ":" + port, e);
        }
    }
}
