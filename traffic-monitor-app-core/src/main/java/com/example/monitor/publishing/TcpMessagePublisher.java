package com.example.monitor.publishing;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.Socket;

@Component
public class TcpMessagePublisher {
    private final MeterRegistry meterRegistry;

    public TcpMessagePublisher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void send(String host, int port, byte[] payload) {
        try (Socket socket = new Socket(host, port)) {
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
            meterRegistry.counter("network_monitor.messages.sent", "transport", "TCP").increment();
        } catch (IOException e) {
            meterRegistry.counter("network_monitor.messages.send_errors", "transport", "TCP").increment();
            throw new IllegalStateException("Failed to send TCP message to " + host + ":" + port, e);
        }
    }
}
