package com.example.monitor.ingestion;

import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.envelope.DefaultEnvelopeHeader;
import com.example.trafficconfig.InterfaceConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageIngestionPipelineTest {

    private static final int STUB_OPCODE = 42;

    @Mock
    private RecentMessageStore recentMessageStore;

    @Mock
    private MessageDefinitionRegistry scopedRegistry;

    @Mock
    private MessageArchiveRepository messageArchiveRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @TempDir
    Path tempDir;

    private MessageIngestionPipeline pipeline;
    private InterfaceConfig interfaceConfig;

    @BeforeEach
    void setUp() {
        pipeline = new MessageIngestionPipeline(
                recentMessageStore, messageArchiveRepository, meterRegistry, new SynchronousExecutorService());

        interfaceConfig = new InterfaceConfig();
        interfaceConfig.setName("Stub Interface");
        interfaceConfig.setPort(5001);
        interfaceConfig.setHeaderType(DefaultEnvelopeHeader.class.getName());
        interfaceConfig.setOpcodeFieldName("opcode");
    }

    private static byte[] stubPayload() {
        return ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});
    }

    @Test
    void ingestForInterface_withValidPayload_storesArchivesAndReturnsPopulatedMessage() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));

        ObservedMessage message = pipeline.ingestForInterface(
                stubPayload(), "TCP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.transportProtocol()).isEqualTo("TCP");
        assertThat(message.remoteAddress()).isEqualTo("127.0.0.1:9000");
        assertThat(message.localPort()).isEqualTo(5001);
        assertThat(message.interfaceName()).isEqualTo("Stub Interface");
        assertThat(message.messageType()).isEqualTo("Stub");
        assertThat(message.parseError()).isNull();

        verify(recentMessageStore).add(message);
        verify(messageArchiveRepository).save(message);

        assertThat(meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Stub Interface", "parseError", "false").count()).isEqualTo(1.0);
        assertThat(meterRegistry.summary("network_monitor.messages.payload_size_bytes", "transport", "TCP").count()).isEqualTo(1);
    }

    @Test
    void ingestForInterface_withMalformedPayload_setsParseError() {
        byte[] malformed = new byte[] {1, 2, 3};

        ObservedMessage message = pipeline.ingestForInterface(
                malformed, "TCP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNotNull();
        assertThat(message.interfaceName()).isEqualTo("Unknown");
        assertThat(message.messageType()).isEqualTo("Unknown");

        verify(recentMessageStore).add(message);
        verify(messageArchiveRepository).save(message);

        assertThat(meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Unknown", "parseError", "true").count()).isEqualTo(1.0);
    }

    @Test
    void ingestForInterface_whenArchiveSaveThrows_incrementsArchiveFailureCounter() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(messageArchiveRepository).save(any());

        pipeline.ingestForInterface(stubPayload(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(meterRegistry.counter("network_monitor.archive.failures", "transport", "UDP").count()).isEqualTo(1.0);
    }

    @Test
    void ingestForInterface_withMessageNotOwningHeader_decodesUsingInterfaceScopedHeaderAndRegistry() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));

        byte[] payload = ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.interfaceName()).isEqualTo("Stub Interface");
        assertThat(message.messageType()).isEqualTo("Stub");
        assertThat(message.parseError()).isNull();
        assertThat(message.header()).containsEntry("opcode", STUB_OPCODE);
        // messageOwnsHeader defaults to false, so the pipeline strips the header before decoding:
        // only the 3 body bytes should reach StubDefinition.decodeBody, not header+body.
        assertThat(message.body()).containsEntry("raw", 3);

        verify(recentMessageStore).add(message);
        verify(messageArchiveRepository).save(message);
    }

    @Test
    void ingestForInterface_withMessageOwningHeader_passesFullPayloadToDefinition() {
        interfaceConfig.setMessageOwnsHeader(true);
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));

        byte[] payload = ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNull();
        // messageOwnsHeader is true, so the full payload (header + body) reaches decodeBody.
        assertThat(message.body()).containsEntry("raw", payload.length);
    }

    /**
     * Regression test for rada-style interfaces: {@code messageOwnsHeader: true} plus a {@code
     * serdesFile:}/{@code serdesHeaderType:} pair means the pipeline must peek the header via
     * {@code SerdesHeaderDecoder} (a JSON-schema {@code record} type) instead of {@code
     * Class.forName(headerType)} - there is no Java header class at all for these interfaces.
     */
    @Test
    void ingestForInterface_withSerdesHeaderType_decodesHeaderWithoutAJavaClass() throws Exception {
        Path serdesFile = tempDir.resolve("test-header.protocol.json");
        Files.writeString(serdesFile, """
                {
                  "types": [
                    { "name": "TestHeader", "kind": "record", "fields": [
                      { "name": "msgCounter", "type": "int32" },
                      { "name": "msgType", "type": "int32" }
                    ] }
                  ],
                  "messages": []
                }
                """);

        interfaceConfig.setMessageOwnsHeader(true);
        interfaceConfig.setSerdesFile(serdesFile.toString());
        interfaceConfig.setSerdesHeaderType("TestHeader");
        interfaceConfig.setOpcodeFieldName("msgType");

        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));

        ByteBuffer buffer = ByteBuffer.allocate(8 + 3);
        buffer.putInt(1); // msgCounter
        buffer.putInt(STUB_OPCODE); // msgType
        buffer.put(new byte[]{1, 2, 3});

        ObservedMessage message = pipeline.ingestForInterface(
                buffer.array(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNull();
        assertThat(message.header()).containsEntry("msgCounter", 1).containsEntry("msgType", STUB_OPCODE);
        // messageOwnsHeader is true, so the full 11-byte payload reaches decodeBody, not just the
        // 3 body bytes after the 8-byte header.
        assertThat(message.body()).containsEntry("raw", 11);
    }

    @Test
    void ingestForInterface_withBodyLengthMismatch_setsParseError() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES + 2);
        buffer.putInt(STUB_OPCODE);
        buffer.putLong(System.currentTimeMillis());
        buffer.putInt(999);
        buffer.put((byte) 1);
        buffer.put((byte) 2);

        ObservedMessage message = pipeline.ingestForInterface(
                buffer.array(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).contains("Invalid bodyLength");
        assertThat(message.interfaceName()).isEqualTo("Unknown");
    }

    @Test
    void ingestForInterface_withLittleEndianByteOrder_decodesHeaderUsingConfiguredOrder() {
        interfaceConfig.setByteOrder("LITTLE_ENDIAN");
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));

        byte[] payload = littleEndianStubPayload();

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNull();
        assertThat(message.interfaceName()).isEqualTo("Stub Interface");
        assertThat(message.header()).containsEntry("opcode", STUB_OPCODE);
    }

    @Test
    void ingestForInterface_withDefaultBigEndianOrder_misparsesLittleEndianHeader() {
        // Sanity check that the previous test's override is actually load-bearing: decoding the
        // same little-endian bytes with the (default) big-endian interface must NOT resolve to
        // the real opcode - proves header decode really does honor the configured byte order,
        // not just default to something that happens to match.
        byte[] payload = littleEndianStubPayload();

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.header()).doesNotContainEntry("opcode", STUB_OPCODE);
    }

    private static byte[] littleEndianStubPayload() {
        byte[] body = {1, 2, 3};
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES + body.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(STUB_OPCODE);
        buffer.putLong(System.currentTimeMillis());
        buffer.putInt(body.length);
        buffer.put(body);
        return buffer.array();
    }

    @Test
    void ingestForInterface_withUnknownOpcode_setsParseError() {
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.empty());

        byte[] payload = ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNotNull();
        assertThat(message.interfaceName()).isEqualTo("Unknown");
    }

    @Test
    void ingestRestOperation_storesArchivesAndReturnsPopulatedMessage() {
        Map<String, Object> header = Map.of("petId", "42");
        Map<String, Object> body = Map.of("name", "Rex");
        byte[] rawPayload = "{\"name\":\"Rex\"}".getBytes();

        ObservedMessage message = pipeline.ingestRestOperation(
                "REST", "127.0.0.1:9000", 5060, "Pets REST Interface", "getPet", header, body, rawPayload, null);

        assertThat(message.transportProtocol()).isEqualTo("REST");
        assertThat(message.interfaceName()).isEqualTo("Pets REST Interface");
        assertThat(message.messageType()).isEqualTo("getPet");
        assertThat(message.header()).isEqualTo(header);
        assertThat(message.body()).isEqualTo(body);
        assertThat(message.parseError()).isNull();

        verify(recentMessageStore).add(message);
        verify(messageArchiveRepository).save(message);

        assertThat(meterRegistry.counter("network_monitor.messages.received",
                "transport", "REST", "interfaceName", "Pets REST Interface", "parseError", "false").count()).isEqualTo(1.0);
    }

    @Test
    void ingestRestOperation_withParseError_stillStoresMessage() {
        byte[] rawPayload = "not json".getBytes();

        ObservedMessage message = pipeline.ingestRestOperation(
                "REST", "127.0.0.1:9000", 5060, "Pets REST Interface", "createPet",
                Map.of(), Map.of(), rawPayload, "Failed to parse JSON request body");

        assertThat(message.parseError()).isEqualTo("Failed to parse JSON request body");
        verify(recentMessageStore).add(message);
    }

    private static final class StubMessage {
    }

    private static final class StubDefinition implements MessageDefinition {
        @Override
        public String interfaceName() {
            return "Stub Interface";
        }

        @Override
        public String messageType() {
            return "Stub";
        }

        @Override
        public int opcode() {
            return STUB_OPCODE;
        }

        @Override
        public Class<?> messageClass() {
            return StubMessage.class;
        }

        @Override
        public Map<String, Object> decodeBody(ByteBuffer body) {
            return Map.of("raw", body.remaining());
        }

        @Override
        public Object decodeMessage(ByteBuffer body) {
            return new StubMessage();
        }

        @Override
        public byte[] encodeBody(Map<String, Object> fields) {
            return new byte[0];
        }

        @Override
        public byte[] encodeBody(Object message) {
            return new byte[0];
        }
    }

    private static final class SynchronousExecutorService extends AbstractExecutorService {
        private volatile boolean shutdown;

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return true;
        }
    }
}
