package com.example.monitor.ingestion;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedDispatcher;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ProtocolMessage;
import com.example.schemacore.MessageDefinitionRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
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
    private MessageDefinitionRegistry messageDefinitionRegistry;

    @Mock
    private AutoReplySettingsService autoReplySettingsService;

    @Mock
    private MessageArchiveRepository messageArchiveRepository;

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private MessageIngestionPipeline pipeline;

    @BeforeEach
    void setUp() {
        pipeline = new MessageIngestionPipeline(
                recentMessageStore, messageArrivedDispatcher, messageDefinitionRegistry,
                autoReplySettingsService, messageArchiveRepository, meterRegistry, new SynchronousExecutorService());
    }

    private static byte[] stubPayload() {
        return ProtocolHeaderCodec.encodeMessage(STUB_OPCODE, System.currentTimeMillis(), new byte[] {1, 2, 3});
    }

    @Test
    void ingest_withValidPayload_storesArchivesAndReturnsPopulatedMessage() {
        StubDefinition definition = new StubDefinition();
        when(messageDefinitionRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

        ObservedMessage message = pipeline.ingest(stubPayload(), "TCP", "127.0.0.1:9000", 5001);

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
    void ingest_withMalformedPayload_setsParseErrorAndNeverDispatches() {
        byte[] malformed = new byte[] {1, 2, 3};

        ObservedMessage message = pipeline.ingest(malformed, "TCP", "127.0.0.1:9000", 5001);

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
    void ingest_whenArchiveSaveThrows_incrementsArchiveFailureCounter() {
        StubDefinition definition = new StubDefinition();
        when(messageDefinitionRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);
        org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(messageArchiveRepository).save(any());

        pipeline.ingest(stubPayload(), "UDP", "127.0.0.1:9000", 5001);

        assertThat(meterRegistry.counter("network_monitor.archive.failures", "transport", "UDP").count()).isEqualTo(1.0);
    }

    @Test
    void ingest_whenAutoReplyEligible_dispatchesWithDestinationConfig() {
        StubDefinition definition = new StubDefinition();
        when(messageDefinitionRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(true);
        when(autoReplySettingsService.interfaceSettings("Stub Interface")).thenReturn(
                Optional.of(new AutoReplySettingsService.InterfaceAutoReplySettings(true, "localhost", 7001, "UDP")));

        pipeline.ingest(stubPayload(), "UDP", "127.0.0.1:9000", 5001);

        verify(messageArrivedDispatcher).dispatch(
                eq("Stub Interface"), eq("Stub"), any(), eq(new DestinationConfig("localhost", 7001, "UDP")));
    }

    @Test
    void ingest_whenAutoReplyEligibleWithTcpDestination_dispatchesWithTcpDestinationConfig() {
        StubDefinition definition = new StubDefinition();
        when(messageDefinitionRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(true);
        when(autoReplySettingsService.interfaceSettings("Stub Interface")).thenReturn(
                Optional.of(new AutoReplySettingsService.InterfaceAutoReplySettings(true, "localhost", 7001, "TCP")));

        pipeline.ingest(stubPayload(), "UDP", "127.0.0.1:9000", 5001);

        verify(messageArrivedDispatcher).dispatch(
                eq("Stub Interface"), eq("Stub"), any(), eq(new DestinationConfig("localhost", 7001, "TCP")));
    }

    @Test
    void ingest_whenAutoReplyIneligible_doesNotDispatch() {
        StubDefinition definition = new StubDefinition();
        when(messageDefinitionRegistry.findByOpcode(STUB_OPCODE)).thenReturn(Optional.of(definition));
        when(autoReplySettingsService.shouldAutoReply("Stub Interface")).thenReturn(false);

        pipeline.ingest(stubPayload(), "UDP", "127.0.0.1:9000", 5001);

        verifyNoInteractions(messageArrivedDispatcher);
    }

    private static final class StubMessage implements ProtocolMessage {
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
        public Class<? extends ProtocolMessage> messageClass() {
            return StubMessage.class;
        }

        @Override
        public Map<String, Object> decodeBody(ByteBuffer body) {
            return Map.of("raw", body.remaining());
        }

        @Override
        public ProtocolMessage decodeMessage(ByteBuffer body) {
            return new StubMessage();
        }

        @Override
        public byte[] encodeBody(Map<String, Object> fields) {
            return new byte[0];
        }

        @Override
        public byte[] encodeBody(ProtocolMessage message) {
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
