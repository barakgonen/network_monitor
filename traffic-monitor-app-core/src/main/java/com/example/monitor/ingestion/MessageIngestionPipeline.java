package com.example.monitor.ingestion;

import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.RecordType;
import com.example.binaryserdes.Type;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.schema.SerdesHeaderDecoder;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.HeaderDecoder;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.reflect.ReflectiveHeaderDecoder;
import com.example.trafficconfig.InterfaceConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MessageIngestionPipeline {
    private static final Logger log = LoggerFactory.getLogger(MessageIngestionPipeline.class);

    private final RecentMessageStore recentMessageStore;
    private final MessageArchiveRepository messageArchiveRepository;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor;
    // Keyed by InterfaceConfig identity, not InterfaceConfig#getKey() - unit tests construct an
    // InterfaceConfig without ever setting a key, and ConcurrentHashMap rejects null keys.
    // UdpIngestionRunner/TcpIngestionRunner reuse the same InterfaceConfig instance across every
    // packet for a given interface, so identity is a stable, always-non-null cache key here.
    private final Map<InterfaceConfig, HeaderDecoder> headerDecoderCache = new ConcurrentHashMap<>();

    @Autowired
    public MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry
    ) {
        this(recentMessageStore, messageArchiveRepository, meterRegistry, Executors.newCachedThreadPool());
    }

    MessageIngestionPipeline(
            RecentMessageStore recentMessageStore,
            MessageArchiveRepository messageArchiveRepository,
            MeterRegistry meterRegistry,
            ExecutorService executor
    ) {
        this.recentMessageStore = recentMessageStore;
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

    /**
     * REST analogue of {@link #ingestForInterface} - skips {@link #decodeForInterface} entirely
     * (there's no opcode/byte-decode step: the JSON body is already a {@code Map<String,Object>}
     * via Jackson). "Auto-reply" for REST is the synchronous HTTP response
     * {@code RestIngestionRunner} writes back on the same exchange.
     */
    public ObservedMessage ingestRestOperation(
            String transportProtocol,
            String remoteAddress,
            int localPort,
            String interfaceName,
            String operationId,
            Map<String, Object> header,
            Map<String, Object> body,
            byte[] rawPayload,
            String parseError
    ) {
        ObservedMessage message = new ObservedMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
                transportProtocol,
                remoteAddress,
                localPort,
                interfaceName,
                operationId,
                header,
                body,
                rawPayload.length,
                new String(rawPayload, StandardCharsets.UTF_8),
                Base64.getEncoder().encodeToString(rawPayload),
                parseError
        );

        storeAndArchive(message);
        return message;
    }

    private ObservedMessage finishIngest(
            byte[] payload, String transportProtocol, String remoteAddress, int localPort, DecodedPacket decoded) {
        ObservedMessage message = toObservedMessage(transportProtocol, remoteAddress, localPort, payload, decoded);
        storeAndArchive(message);
        return message;
    }

    private void storeAndArchive(ObservedMessage message) {
        recordMetrics(message);
        recentMessageStore.add(message);
        archiveMessage(message);
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
            HeaderDecoder headerDecoder = resolveHeaderDecoder(interfaceConfig);
            int headerSize = headerDecoder.headerSize();

            if (payload.length < headerSize) {
                throw new IllegalArgumentException(
                        "Payload shorter than configured header. payloadBytes=" + payload.length
                                + ", headerBytes=" + headerSize);
            }

            byte[] headerBytes = java.util.Arrays.copyOfRange(payload, 0, headerSize);
            Map<String, Object> headerFields = headerDecoder.decode(headerBytes, interfaceConfig.resolveByteOrder());

            if (!interfaceConfig.isMessageOwnsHeader()) {
                validateBodyLength(headerFields, interfaceConfig, payload.length - headerSize);
            }

            Object opcodeValue = headerFields.get(interfaceConfig.getOpcodeFieldName());
            long opcode = coerceOpcode(opcodeValue);

            MessageDefinition definition = scopedRegistry.findByOpcode(opcode)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown opcode " + Long.toUnsignedString(opcode) + " for interface " + interfaceConfig.getName()));

            Map<String, Object> bodyFields = definition.decodeBody(bodyBuffer(payload, headerSize, interfaceConfig));
            Object typedMessage = definition.decodeMessage(bodyBuffer(payload, headerSize, interfaceConfig));

            return new DecodedPacket(definition, headerFields, bodyFields, typedMessage, null);
        } catch (Exception e) {
            return new DecodedPacket(null, null, null, null, e.getMessage());
        }
    }

    /**
     * The decoded header field's value is already a boxed {@link Number} whose actual type
     * (Integer/Long) is entirely determined by the header struct's own field declaration (see
     * {@code ReflectiveFieldExtractor}/{@code SerdesHeaderDecoder}) - so this widens via {@code
     * longValue()} rather than round-tripping through a string, which would silently break for any
     * opcode outside {@code int} range. The string-parsing fallback exists only for defense in
     * depth (a header decoder producing something other than a {@code Number} would be a bug
     * elsewhere), trying signed parsing first and falling back to unsigned for literals in the
     * upper half of the 64-bit range.
     */
    private long coerceOpcode(Object opcodeValue) {
        if (opcodeValue instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(opcodeValue);
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return Long.parseUnsignedLong(text);
        }
    }

    /**
     * Built lazily per interface and cached thereafter (rather than injected as a Spring-wired
     * map like {@code interfaceMessageDefinitionRegistries}) so this class's constructor - and
     * every existing test constructing it directly - stays unchanged. Class.forName is cheap
     * after the JVM's own classloader caching kicks in, but re-parsing a serdes JSON file on
     * every single incoming packet would not be, hence the cache.
     */
    private HeaderDecoder resolveHeaderDecoder(InterfaceConfig interfaceConfig) throws Exception {
        HeaderDecoder cached = headerDecoderCache.get(interfaceConfig);
        if (cached != null) {
            return cached;
        }

        HeaderDecoder decoder = buildHeaderDecoder(interfaceConfig);
        headerDecoderCache.put(interfaceConfig, decoder);
        return decoder;
    }

    /**
     * {@code messageOwnsHeader} interfaces with both a {@code serdesFile:} and a {@code
     * serdesHeaderType:} (e.g. rada) decode their header via that file's own {@code record} type
     * instead of a Java class - see {@code SerdesHeaderDecoder}. Every other interface (including
     * every {@code messageOwnsHeader: false} legacy-envelope one) keeps the original
     * {@code headerType:} Class-based behavior unchanged.
     */
    private HeaderDecoder buildHeaderDecoder(InterfaceConfig interfaceConfig) throws Exception {
        if (interfaceConfig.hasSerdesFile() && interfaceConfig.hasSerdesHeaderType()) {
            ProtocolConfig protocolConfig;
            try (InputStream in = Files.newInputStream(Paths.get(interfaceConfig.getSerdesFile()))) {
                protocolConfig = Protocol.loadConfig(in);
            }

            Type<?> headerType = Protocol.resolveNamedType(protocolConfig, interfaceConfig.getSerdesHeaderType());
            if (!(headerType instanceof RecordType recordType)) {
                throw new IllegalStateException(
                        "serdesHeaderType '" + interfaceConfig.getSerdesHeaderType() + "' for interface "
                                + interfaceConfig.getKey() + " must be a record type");
            }

            return new SerdesHeaderDecoder(recordType);
        }

        Class<?> headerType = Class.forName(interfaceConfig.getHeaderType());
        return new ReflectiveHeaderDecoder(headerType);
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
            Object typedMessage,
            String parseError
    ) {
    }
}
