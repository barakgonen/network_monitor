package com.example.monitor.publishing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

@Component
public class UdpMessagePublisher {
    private final MeterRegistry meterRegistry;

    public UdpMessagePublisher(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void send(String host, int port, byte[] payload) {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName(host);
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
            meterRegistry.counter("network_monitor.messages.sent", "transport", "UDP").increment();
        } catch (IOException e) {
            meterRegistry.counter("network_monitor.messages.send_errors", "transport", "UDP").increment();
            throw new IllegalStateException("Failed to send UDP packet to " + host + ":" + port, e);
        }
    }
}
