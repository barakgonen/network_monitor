package com.example.monitor.ingestion;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ProtocolMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MessageIngestionPipeline {
    private static final Logger log = LoggerFactory.getLogger(MessageIngestionPipeline.class);

    private final RecentMessageStore recentMessageStore;
    private final MessageArrivedDispatcher messageArrivedDispatcher;
    private final MessageDefinitionRegistry messageDefinitionRegistry;
    private final AutoReplySettingsService autoReplySettingsService;
    private final MessageArchiveRepository messageArchiveRepository;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;

    @Autowired
    public MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            MessageDefinitionRegistry messageDefinitionRegistry,
            AutoReplySettingsService autoReplySettingsService,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry
    ) {
        this(recentMessageStore, messageArrivedDispatcher, messageDefinitionRegistry, autoReplySettingsService,
                messageArchiveRepository, meterRegistry, Executors.newCachedThreadPool());
    }

    MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            MessageDefinitionRegistry messageDefinitionRegistry,
            AutoReplySettingsService autoReplySettingsService,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry,
            ExecutorService executor
    ) {
        this.recentMessageStore = recentMessageStore;
        this.messageArrivedDispatcher = messageArrivedDispatcher;
        this.messageDefinitionRegistry = messageDefinitionRegistry;
        this.autoReplySettingsService = autoReplySettingsService;
        this.messageArchiveRepository = messageArchiveRepository;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    public ObservedMessage ingest(byte[] payload, String transportProtocol, String remoteAddress, int localPort) {
        DecodedPacket decoded = decode(payload);
        ObservedMessage message = toObservedMessage(transportProtocol, remoteAddress, localPort, payload, decoded);
        recordMetrics(message);
        recentMessageStore.add(message);
        archiveMessage(message);
        dispatchIfEligible(decoded);
        return message;
    }

    private void recordMetrics(ObservedMessage message) {
        Counter.builder("network_monitor.messages.received")
                .tag("transport", message.transportProtocol())
                .tag("interfaceName", message.interfaceName())
                .tag("parseError", message.parseError() != null ? "true" : "false")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("network_monitor.messages.payload_size_bytes")
                .tag("transport", message.transportProtocol())
                .register(meterRegistry)
                .record(message.payloadSizeBytes());
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

    private void archiveMessage(ObservedMessage message) {
        executor.submit(() -> {
            try {
                messageArchiveRepository.save(message);
            } catch (Exception e) {
                log.warn("Failed to archive message {} ({}): {}", message.id(), message.messageType(), e.getMessage(), e);
                Counter.builder("network_monitor.archive.failures")
                        .tag("transport", message.transportProtocol())
                        .register(meterRegistry)
                        .increment();
            }
        });
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
                .map(settings -> new DestinationConfig(settings.host(), settings.port(), settings.transport()))
                .orElse(null);

        executor.submit(() -> {
            try {
                messageArrivedDispatcher.dispatch(interfaceName, messageType, decoded.typedMessage(), destinationConfig);
            } catch (Exception e) {
                log.warn("onMessageArrived handler failed for {}/{}: {}", interfaceName, messageType, e.getMessage(), e);
                Counter.builder("network_monitor.dispatch.failures")
                        .tag("interfaceName", interfaceName)
                        .register(meterRegistry)
                        .increment();
            }
        });
    }

    private ObservedMessage toObservedMessage(
            String transportProtocol, String remoteAddress, int localPort, byte[] payload, DecodedPacket decoded) {
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
                transportProtocol,
                remoteAddress,
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
    public void shutdown() {
        executor.shutdownNow();
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
