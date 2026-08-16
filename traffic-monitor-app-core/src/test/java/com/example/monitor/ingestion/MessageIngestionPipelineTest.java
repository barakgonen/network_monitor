package com.example.monitor.ingestion;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.envelope.DefaultEnvelopeHeader;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.MessageDefinitionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageIngestionPipelineTest {

    private static final int STUB_OPCODE = 42;

    @Mock
    private RecentMessageStore recentMessageStore;

    @Mock
    private MessageArrivedDispatcher messageArrivedDispatcher;

    @Mock
    private MessageDefinitionRegistry scopedRegistry;

    @Mock
    private AutoReplySettingsService autoReplySettingsService;

    @Mock
    private MessageArchiveRepository messageArchiveRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private MessageIngestionPipeline pipeline;
    private InterfaceConfig interfaceConfig;

    @BeforeEach
    void setUp() {
        pipeline = new MessageIngestionPipeline(
                recentMessageStore, messageArrivedDispatcher,
                autoReplySettingsService, messageArchiveRepository, meterRegistry, new SynchronousExecutorService());

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
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

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
        verify(messageArrivedDispatcher, never()).dispatch(any(), any(), any(), any());

        assertThat(meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Stub Interface", "parseError", "false").count()).isEqualTo(1.0);
        assertThat(meterRegistry.summary("network_monitor.messages.payload_size_bytes", "transport", "TCP").count()).isEqualTo(1);
    }

    @Test
    void ingestForInterface_withMalformedPayload_setsParseErrorAndNeverDispatches() {
        byte[] malformed = new byte[] {1, 2, 3};

        ObservedMessage message = pipeline.ingestForInterface(
                malformed, "TCP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNotNull();
        assertThat(message.interfaceName()).isEqualTo("Unknown");
        assertThat(message.messageType()).isEqualTo("Unknown");

        verify(recentMessageStore).add(message);
        verify(messageArchiveRepository).save(message);
        verifyNoInteractions(messageArrivedDispatcher);

        assertThat(meterRegistry.counter("network_monitor.messages.received",
                "transport", "TCP", "interfaceName", "Unknown", "parseError", "true").count()).isEqualTo(1.0);
    }

    @Test
    void ingestForInterface_whenArchiveSaveThrows_incrementsArchiveFailureCounter() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(messageArchiveRepository).save(any());

        pipeline.ingestForInterface(stubPayload(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(meterRegistry.counter("network_monitor.archive.failures", "transport", "UDP").count()).isEqualTo(1.0);
    }

    @Test
    void ingestForInterface_whenAutoReplyEligible_dispatchesWithDestinationConfig() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(true);
        when(autoReplySettingsService.interfaceSettings("Stub Interface")).thenReturn(
                Optional.of(new AutoReplySettingsService.InterfaceAutoReplySettings(true, "localhost", 7001, "UDP")));

        pipeline.ingestForInterface(stubPayload(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        verify(messageArrivedDispatcher).dispatch(
                eq("Stub Interface"), eq("Stub"), any(), eq(new DestinationConfig("localhost", 7001, "UDP")));
    }

    @Test
    void ingestForInterface_whenAutoReplyEligibleWithTcpDestination_dispatchesWithTcpDestinationConfig() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(true);
        when(autoReplySettingsService.interfaceSettings("Stub Interface")).thenReturn(
                Optional.of(new AutoReplySettingsService.InterfaceAutoReplySettings(true, "localhost", 7001, "TCP")));

        pipeline.ingestForInterface(stubPayload(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        verify(messageArrivedDispatcher).dispatch(
                eq("Stub Interface"), eq("Stub"), any(), eq(new DestinationConfig("localhost", 7001, "TCP")));
    }

    @Test
    void ingestForInterface_whenAutoReplyIneligible_doesNotDispatch() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

        pipeline.ingestForInterface(stubPayload(), "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        verifyNoInteractions(messageArrivedDispatcher);
    }

    @Test
    void ingestForInterface_withMessageNotOwningHeader_decodesUsingInterfaceScopedHeaderAndRegistry() {
        StubDefinition definition = new StubDefinition();
        when(scopedRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

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
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

        byte[] payload = ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 5001, interfaceConfig, scopedRegistry);

        assertThat(message.parseError()).isNull();
        // messageOwnsHeader is true, so the full payload (header + body) reaches decodeBody.
        assertThat(message.body()).containsEntry("raw", payload.length);
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
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

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
