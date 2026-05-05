package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile boolean running;
    private DatagramSocket socket;

    public UdpIngestionRunner(TrafficMonitorProperties properties, RecentMessageStore recentMessageStore) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
    }

    @PostConstruct
    public void start() {
        if (!properties.getUdp().isEnabled()) {
            log.info("UDP ingestion is disabled");
            return;
        }

        running = true;
        executor.submit(this::listen);
    }

    private void listen() {
        int port = properties.getUdp().getPort();
        int bufferSize = properties.getUdp().getBufferSizeBytes();

        try {
            socket = new DatagramSocket(port);
            log.info("UDP ingestion started on port {}", port);

            while (running) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                String payloadText = new String(payload, StandardCharsets.UTF_8);
                String payloadBase64 = Base64.getEncoder().encodeToString(payload);

                ObservedMessage message = new ObservedMessage(
                        UUID.randomUUID().toString(),
                        Instant.now(),
                        "UDP",
                        packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                        port,
                        payload.length,
                        payloadText,
                        payloadBase64,
                        null
                );

                recentMessageStore.add(message);

                log.info("Received UDP packet from {}:{} on port {} - {} bytes - text='{}'",
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        port,
                        payload.length,
                        payloadText);
            }
        } catch (Exception e) {
            if (running) {
                log.error("UDP ingestion failed", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        executor.shutdownNow();
        log.info("UDP ingestion stopped");
    }
}
