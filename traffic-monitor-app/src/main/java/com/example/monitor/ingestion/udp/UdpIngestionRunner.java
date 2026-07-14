package com.example.monitor.ingestion.udp;

import com.example.handlercore.IncomingMessage;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final MessageArrivedDispatcher messageArrivedDispatcher;
    private final MessageDefinitionRegistry messageDefinitionRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DatagramSocket> sockets = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    public UdpIngestionRunner(
            TrafficMonitorProperties properties,
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            MessageDefinitionRegistry messageDefinitionRegistry
    ) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.messageArrivedDispatcher = messageArrivedDispatcher;
        this.messageDefinitionRegistry = messageDefinitionRegistry;
    }

    @PostConstruct
    public void start() {
        if (!properties.getUdp().isEnabled()) {
            log.info("UDP ingestion is disabled");
            return;
        }

        running = true;
        executor.submit(() -> listen(properties.getUdp().getFruitPort()));
        executor.submit(() -> listen(properties.getUdp().getWeatherPort()));
    }

    private void listen(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            sockets.add(socket);
            log.info("UDP ingestion started on port {}", port);

            int bufferSize = properties.getUdp().getBufferSizeBytes();

            while (running) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                ObservedMessage message = toObservedMessage(packet, port, payload);
                recentMessageStore.add(message);
                dispatchIfParsed(packet, port, message);

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
                log.error("UDP ingestion failed on port {}", port, e);
            }
        }
    }

    private void dispatchIfParsed(DatagramPacket packet, int localPort, ObservedMessage message) {
        if (message.parseError() != null) {
            return;
        }

        IncomingMessage incoming = new IncomingMessage(
                message.interfaceName(),
                message.messageType(),
                packet.getAddress().getHostAddress(),
                packet.getPort(),
                localPort,
                message.observedAt().toEpochMilli(),
                message.header(),
                message.body()
        );

        executor.submit(() -> {
            try {
                messageArrivedDispatcher.dispatch(incoming);
            } catch (Exception e) {
                log.warn("onMessageArrived handler failed for {}/{}: {}",
                        incoming.interfaceName(), incoming.messageType(), e.getMessage(), e);
            }
        });
    }

    private ObservedMessage toObservedMessage(DatagramPacket packet, int localPort, byte[] payload) {
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);

        String interfaceName = "Unknown";
        String messageType = "Unknown";
        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        String parseError = null;

        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            ProtocolHeader decodedHeader = ProtocolHeaderCodec.decodeHeader(buffer);

            MessageDefinition definition = messageDefinitionRegistry.findByOpcode(decodedHeader.opcode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown opcode: " + decodedHeader.opcode()));

            interfaceName = definition.interfaceName();
            messageType = definition.messageType();

            header.put("opcode", decodedHeader.opcode());
            header.put("sendTimeEpochMillis", decodedHeader.sendTimeEpochMillis());
            header.put("bodyLength", decodedHeader.bodyLength());
            body.putAll(definition.decodeBody(buffer));
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

        for (DatagramSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.close();
            }
        }

        executor.shutdownNow();
        log.info("UDP ingestion stopped");
    }
}
