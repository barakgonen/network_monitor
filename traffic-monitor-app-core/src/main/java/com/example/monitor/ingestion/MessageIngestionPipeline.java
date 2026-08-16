package com.example.monitor.ingestion;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolMessage;
import com.example.schemacore.reflect.ReflectiveFieldExtractor;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemacore.reflect.StructSizeCalculator;
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
    private final AutoReplySettingsService autoReplySettingsService;
    private final MessageArchiveRepository messageArchiveRepository;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;

    @Autowired
    public MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            AutoReplySettingsService autoReplySettingsService,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry
    ) {
        this(recentMessageStore, messageArrivedDispatcher, autoReplySettingsService,
                messageArchiveRepository, meterRegistry, Executors.newCachedThreadPool());
    }

    MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArrivedDispatcher messageArrivedDispatcher,
            AutoReplySettingsService autoReplySettingsService,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry,
            ExecutorService executor
    ) {
        this.recentMessageStore = recentMessageStore;
        this.messageArrivedDispatcher = messageArrivedDispatcher;
        this.autoReplySettingsService = autoReplySettingsService;
        this.messageArchiveRepository = messageArchiveRepository;
        this.meterRegistry = meterRegistry;
        this.executor = executor;
    }

    /**
     * Decodes a packet against a single {@link InterfaceConfig} (its own header type, byte order,
     * and opcode field) and its own scoped {@link MessageDefinitionRegistry}.
     */
    public ObservedMessage ingestForInterface(
            byte[] payload,
            String transportProtocol,
            String remoteAddress,
            int localPort,
            InterfaceConfig interfaceConfig,
            MessageDefinitionRegistry scopedRegistry
    ) {
        DecodedPacket decoded = decodeForInterface(payload, interfaceConfig, scopedRegistry);
        return finishIngest(payload, transportProtocol, remoteAddress, localPort, decoded);
    }

    private ObservedMessage finishIngest(
            byte[] payload, String transportProtocol, String remoteAddress, int localPort, DecodedPacket decoded) {
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

    /**
     * Resolves the header type, opcode field, and message registry from a single
     * {@link InterfaceConfig}. Whether the body-decode buffer includes the header bytes depends
     * on {@link InterfaceConfig#isMessageOwnsHeader()}: {@code true} (e.g. rada) means the message
     * class parses its own header itself, so it needs the full payload; {@code false} (the
     * default) means the pipeline strips the header first, since the message class only ever
     * handles body-only bytes.
     */
    private DecodedPacket decodeForInterface(
            byte[] payload, InterfaceConfig interfaceConfig, MessageDefinitionRegistry scopedRegistry) {
        try {
            Class<?> headerType = Class.forName(interfaceConfig.getHeaderType());
            int headerSize = StructSizeCalculator.calculateStructSize(headerType);

            if (payload.length < headerSize) {
                throw new IllegalArgumentException(
                        "Payload shorter than configured header. payloadBytes=" + payload.length
                                + ", headerBytes=" + headerSize);
            }

            byte[] headerBytes = java.util.Arrays.copyOfRange(payload, 0, headerSize);
            Object header = ReflectiveStructCodec.decode(headerType, headerBytes, interfaceConfig.resolveByteOrder());
            Map<String, Object> headerFields = ReflectiveFieldExtractor.extractFields(header);

            if (!interfaceConfig.isMessageOwnsHeader()) {
                validateBodyLength(headerFields, interfaceConfig, payload.length - headerSize);
            }

            Object opcodeValue = headerFields.get(interfaceConfig.getOpcodeFieldName());
            int opcode = Integer.parseInt(String.valueOf(opcodeValue));

            MessageDefinition definition = scopedRegistry.findByOpcode(opcode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown opcode " + opcode + " for interface " + interfaceConfig.getName()));

            Map<String, Object> bodyFields = definition.decodeBody(bodyBuffer(payload, headerSize, interfaceConfig));
            ProtocolMessage typedMessage = definition.decodeMessage(bodyBuffer(payload, headerSize, interfaceConfig));

            return new DecodedPacket(definition, headerFields, bodyFields, typedMessage, null);
        } catch (Exception e) {
            return new DecodedPacket(null, null, null, null, e.getMessage());
        }
    }

    /**
     * Mirrors {@code ProtocolHeaderCodec.decodeHeader}'s bodyLength-vs-actual-remaining-bytes
     * check, generalized to any header type via {@link InterfaceConfig#getBodyLengthFieldName()}.
     * Only meaningful when the pipeline (not the message class) owns header interpretation - the
     * field's semantics aren't guaranteed for {@code messageOwnsHeader} interfaces.
     */
    private void validateBodyLength(Map<String, Object> headerFields, InterfaceConfig interfaceConfig, int actualBodyLength) {
        Object bodyLengthValue = headerFields.get(interfaceConfig.getBodyLengthFieldName());
        if (bodyLengthValue == null) {
            return;
        }

        int declaredBodyLength = Integer.parseInt(String.valueOf(bodyLengthValue));
        if (declaredBodyLength != actualBodyLength) {
            throw new IllegalArgumentException(
                    "Invalid bodyLength. header=" + declaredBodyLength + ", actualRemaining=" + actualBodyLength);
        }
    }

    private ByteBuffer bodyBuffer(byte[] payload, int headerSize, InterfaceConfig interfaceConfig) {
        if (interfaceConfig.isMessageOwnsHeader()) {
            return ByteBuffer.wrap(payload);
        }
        return ByteBuffer.wrap(payload, headerSize, payload.length - headerSize);
    }

    private void archiveMessage(ObservedMessage message) {
        executor.submit(() -> {
            try {
                messageArchiveRepository.save(message);
            } catch (Exception e) {
                log.warn("Failed to archive message {} ({}): {}", message.id(), message.messageType(), e.getMessage(), e);
                incrementArchiveFailureCounter(message);
            }
        });
    }

    private void incrementArchiveFailureCounter(ObservedMessage message) {
        Counter.builder("network_monitor.archive.failures")
                .tag("transport", message.transportProtocol())
                .register(meterRegistry)
                .increment();
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
                incrementDispatchFailureCounter(interfaceName);
            }
        });
    }

    private void incrementDispatchFailureCounter(String interfaceName) {
        Counter.builder("network_monitor.dispatch.failures")
                .tag("interfaceName", interfaceName)
                .register(meterRegistry)
                .increment();
    }

    private ObservedMessage toObservedMessage(
            String transportProtocol, String remoteAddress, int localPort, byte[] payload, DecodedPacket decoded) {
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);

        String interfaceName = decoded.definition() != null ? decoded.definition().interfaceName() : "Unknown";
        String messageType = decoded.definition() != null ? decoded.definition().messageType() : "Unknown";

        Map<String, Object> header = decoded.header() != null ? decoded.header() : new LinkedHashMap<>();
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
            Map<String, Object> header,
            Map<String, Object> bodyFields,
            ProtocolMessage typedMessage,
            String parseError
    ) {
    }
}
