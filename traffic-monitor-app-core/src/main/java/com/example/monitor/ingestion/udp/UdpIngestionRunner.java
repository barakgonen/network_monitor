package com.example.monitor.ingestion.udp;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ProtocolMessage;
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
    private final AutoReplySettingsService autoReplySettingsService;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DatagramSocket> sockets = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    public UdpIngestionRunner(
            TrafficMonitorProperties properties,
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            MessageDefinitionRegistry messageDefinitionRegistry,
            AutoReplySettingsService autoReplySettingsService
    ) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.messageArrivedDispatcher = messageArrivedDispatcher;
        this.messageDefinitionRegistry = messageDefinitionRegistry;
        this.autoReplySettingsService = autoReplySettingsService;
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
                DecodedPacket decoded = decode(payload);
                ObservedMessage message = toObservedMessage(packet, port, payload, decoded);
                recentMessageStore.add(message);
                dispatchIfEligible(decoded);

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

    private DecodedPacket decode(byte[] payload) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

            MessageDefinition definition = messageDefinitionRegistry.findByOpcode(header.opcode())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown opcode: " + header.opcode()));

            int bodyStart = buffer.position();
            Map<String, Object> bodyFields = definition.decodeBody(buffer);
            ProtocolMessage typedMessage = definition.decodeMessage(
                    ByteBuffer.wrap(payload, bodyStart, payload.length - bodyStart));

            return new DecodedPacket(definition, header, bodyFields, typedMessage, null);
        } catch (Exception e) {
            return new DecodedPacket(null, null, null, null, e.getMessage());
        }
    }

    private void dispatchIfEligible(DecodedPacket decoded) {
        if (decoded.parseError() != null) {
            return;
        }

        String interfaceName = decoded.definition().interfaceName();
        String messageType = decoded.definition().messageType();

        if (!autoReplySettingsService.shouldAutoReply(interfaceName)) {
            return;
        }

        DestinationConfig destinationConfig = autoReplySettingsService.interfaceSettings(interfaceName)
                .map(settings -> new DestinationConfig(settings.host(), settings.port()))
                .orElse(null);

        executor.submit(() -> {
            try {
                messageArrivedDispatcher.dispatch(interfaceName, messageType, decoded.typedMessage(), destinationConfig);
            } catch (Exception e) {
                log.warn("onMessageArrived handler failed for {}/{}: {}", interfaceName, messageType, e.getMessage(), e);
            }
        });
    }

    private ObservedMessage toObservedMessage(DatagramPacket packet, int localPort, byte[] payload, DecodedPacket decoded) {
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);

        String interfaceName = decoded.definition() != null ? decoded.definition().interfaceName() : "Unknown";
        String messageType = decoded.definition() != null ? decoded.definition().messageType() : "Unknown";

        Map<String, Object> header = new LinkedHashMap<>();
        if (decoded.header() != null) {
            header.put("opcode", decoded.header().opcode());
            header.put("sendTimeEpochMillis", decoded.header().sendTimeEpochMillis());
            header.put("bodyLength", decoded.header().bodyLength());
        }

        Map<String, Object> body = decoded.bodyFields() != null ? decoded.bodyFields() : new LinkedHashMap<>();

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
                decoded.parseError()
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

    private record DecodedPacket(
            MessageDefinition definition,
            ProtocolHeader header,
            Map<String, Object> bodyFields,
            ProtocolMessage typedMessage,
            String parseError
    ) {
    }
}
