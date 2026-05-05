package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemas.fruit.FruitProtocolCodec;
import com.example.schemas.fruit.FruitProtocolHeader;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final FruitProtocolCodec fruitProtocolCodec = new FruitProtocolCodec();

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
                ObservedMessage message = toObservedMessage(packet, port, payload);
                recentMessageStore.add(message);

                log.info("Received UDP {} message from {}:{} on port {} - {} bytes - type={} - parseError={}",
                        message.interfaceName(),
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        port,
                        payload.length,
                        message.messageType(),
                        message.parseError());
            }
        } catch (Exception e) {
            if (running) {
                log.error("UDP ingestion failed", e);
            }
        }
    }

    private ObservedMessage toObservedMessage(DatagramPacket packet, int localPort, byte[] payload) {
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);

        String interfaceName = "Fruit Interface";
        String messageType = "Unknown";
        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        String parseError = null;

        try {
            FruitProtocolCodec.DecodedFruitMessage decoded = fruitProtocolCodec.decode(payload);
            FruitProtocolHeader decodedHeader = decoded.header();

            interfaceName = decoded.interfaceName();
            messageType = decoded.messageType();

            header.put("opcode", decodedHeader.opcode());
            header.put("sendTimeEpochMillis", decodedHeader.sendTimeEpochMillis());
            header.put("bodyLength", decodedHeader.bodyLength());

            body.putAll(decoded.bodyFields());
        } catch (Exception e) {
            parseError = e.getMessage();
        }

        return new ObservedMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                "UDP",
                packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                localPort,
                interfaceName,
                messageType,
                header,
                body,
                payload.length,
                payloadText,
                payloadBase64,
                parseError
        );
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
