package com.example.monitor.ingestion;

import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.schema.MessageSchemaWiringConfig;
import com.example.monitor.store.RecentMessageStore;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Real-world regression scenario for the wide-opcode fix: a header built from 5 plain integers
 * (messageId, messageLength, messageTimeTag, messageSequenceNumber, messageSourceId), where
 * {@code messageId} (the opcode) is an unsigned 32-bit ID in the {@code 0xCEF0xxxx} range - well
 * above {@link Integer#MAX_VALUE} once read as unsigned, which is exactly the case the old {@code
 * Integer.parseInt(String.valueOf(opcodeValue))} in {@code MessageIngestionPipeline} could not
 * handle. Exercises the real production wiring ({@link MessageSchemaWiringConfig}) end to end
 * against {@link MessageIngestionPipeline}, not a mocked registry, to prove the whole mechanism -
 * config to bytes to routed, parsed message - actually works for these opcodes.
 */
class WideOpcodeHeaderIngestionTest {

    // The 5-int header shape (all values as unsigned wire bit patterns, matching how a client
    // would actually construct these): messageId is deliberately >= 0x80000000 so it reads as
    // negative if ever mistakenly treated as a signed int32.
    private static final int KEEP_ALIVE_ID = (int) 0xCEF00400L;
    private static final int MSG_0401_ID = (int) 0xCEF00401L;
    private static final int MSG_0402_ID = (int) 0xCEF00402L;
    private static final int MSG_0403_ID = (int) 0xCEF00403L;
    private static final int MSG_041A_ID = (int) 0xCEF0041AL;

    // Same bit patterns, read as unsigned - what MessageType.getOpcode()/findByOpcode should see.
    private static final long KEEP_ALIVE_OPCODE = Integer.toUnsignedLong(KEEP_ALIVE_ID);
    private static final long MSG_0401_OPCODE = Integer.toUnsignedLong(MSG_0401_ID);
    private static final long MSG_0402_OPCODE = Integer.toUnsignedLong(MSG_0402_ID);
    private static final long MSG_0403_OPCODE = Integer.toUnsignedLong(MSG_0403_ID);
    private static final long MSG_041A_OPCODE = Integer.toUnsignedLong(MSG_041A_ID);

    @TempDir
    Path tempDir;

    private MessageIngestionPipeline pipeline;
    private InterfaceConfig interfaceConfig;
    private MessageDefinitionRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        RecentMessageStore recentMessageStore = mock(RecentMessageStore.class);
        MessageArchiveRepository messageArchiveRepository = mock(MessageArchiveRepository.class);
        pipeline = new MessageIngestionPipeline(
                recentMessageStore, messageArchiveRepository, new SimpleMeterRegistry(), new SynchronousExecutorService());

        Path serdesFile = tempDir.resolve("wide-opcode-header.protocol.json");
        Files.writeString(serdesFile, """
                {
                  "types": [
                    { "name": "MessageHeader", "kind": "record", "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" }
                    ] }
                  ],
                  "messages": [
                    { "name": "KeepAliveMessage", "opcode": %d, "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" }
                    ] },
                    { "name": "Message0401", "opcode": %d, "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" },
                      { "name": "payloadValue", "type": "int32" }
                    ] },
                    { "name": "Message0402", "opcode": %d, "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" },
                      { "name": "payloadValue", "type": "int32" }
                    ] },
                    { "name": "Message0403", "opcode": %d, "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" },
                      { "name": "payloadValue", "type": "int32" }
                    ] },
                    { "name": "Message041A", "opcode": %d, "fields": [
                      { "name": "messageId", "type": "uint32" },
                      { "name": "messageLength", "type": "int32" },
                      { "name": "messageTimeTag", "type": "int32" },
                      { "name": "messageSequenceNumber", "type": "int32" },
                      { "name": "messageSourceId", "type": "int32" },
                      { "name": "payloadValue", "type": "int32" }
                    ] }
                  ]
                }
                """.formatted(
                KEEP_ALIVE_OPCODE, MSG_0401_OPCODE, MSG_0402_OPCODE, MSG_0403_OPCODE, MSG_041A_OPCODE));

        interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey("wide-header-iface");
        interfaceConfig.setName("Wide Header Interface");
        interfaceConfig.setPort(1);
        interfaceConfig.setMessageOwnsHeader(true);
        interfaceConfig.setSerdesFile(serdesFile.toString());
        interfaceConfig.setSerdesHeaderType("MessageHeader");
        interfaceConfig.setOpcodeFieldName("messageId");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(interfaceConfig));

        Map<String, MessageDefinitionRegistry> registries =
                new MessageSchemaWiringConfig().interfaceMessageDefinitionRegistries(trafficToolConfig);
        registry = registries.get("wide-header-iface");
    }

    /** The message-level opcodes really did parse as the expected unsigned 64-bit values. */
    @Test
    void configuredOpcodes_resolveToTheExactUnsignedBitPatterns() {
        assertThat(KEEP_ALIVE_OPCODE).isEqualTo(3_471_836_160L);
        assertThat(MSG_0401_OPCODE).isEqualTo(3_471_836_161L);
        assertThat(MSG_0402_OPCODE).isEqualTo(3_471_836_162L);
        assertThat(MSG_0403_OPCODE).isEqualTo(3_471_836_163L);
        assertThat(MSG_041A_OPCODE).isEqualTo(3_471_836_186L);

        assertThat(registry.findByOpcode(KEEP_ALIVE_OPCODE)).isPresent();
        assertThat(registry.findByOpcode(MSG_0401_OPCODE)).isPresent();
        assertThat(registry.findByOpcode(MSG_0402_OPCODE)).isPresent();
        assertThat(registry.findByOpcode(MSG_0403_OPCODE)).isPresent();
        assertThat(registry.findByOpcode(MSG_041A_OPCODE)).isPresent();
    }

    @Test
    void keepAliveMessage_headerOnlyPayload_isRoutedAndParsed() {
        byte[] payload = headerOnlyPayload(KEEP_ALIVE_ID, 20, 111, 1, 7);

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 1, interfaceConfig, registry);

        assertThat(message.parseError()).isNull();
        assertThat(message.messageType()).isEqualTo("KeepAliveMessage");
        assertThat(message.header()).containsEntry("messageId", KEEP_ALIVE_OPCODE);
        assertThat(message.body())
                .containsEntry("messageId", KEEP_ALIVE_OPCODE)
                .containsEntry("messageSequenceNumber", 1)
                .containsEntry("messageSourceId", 7);
    }

    @Test
    void message0401_withPayloadValue_isRoutedAndParsed() {
        byte[] payload = payloadWithExtraField(MSG_0401_ID, 24, 222, 2, 9, 12345);

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 1, interfaceConfig, registry);

        assertThat(message.parseError()).isNull();
        assertThat(message.messageType()).isEqualTo("Message0401");
        assertThat(message.header()).containsEntry("messageId", MSG_0401_OPCODE);
        assertThat(message.body())
                .containsEntry("messageSequenceNumber", 2)
                .containsEntry("payloadValue", 12345);
    }

    @Test
    void message0402_isRoutedToItsOwnDefinition_notMessage0401s() {
        byte[] payload = payloadWithExtraField(MSG_0402_ID, 24, 333, 3, 10, 999);

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 1, interfaceConfig, registry);

        assertThat(message.parseError()).isNull();
        assertThat(message.messageType()).isEqualTo("Message0402");
        assertThat(message.body()).containsEntry("payloadValue", 999);
    }

    @Test
    void message0403_isRoutedAndParsed() {
        byte[] payload = payloadWithExtraField(MSG_0403_ID, 24, 444, 4, 11, 555);

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 1, interfaceConfig, registry);

        assertThat(message.parseError()).isNull();
        assertThat(message.messageType()).isEqualTo("Message0403");
        assertThat(message.body()).containsEntry("payloadValue", 555);
    }

    /** Non-contiguous opcode (0xCEF0041A, not 0xCEF00404) - proves routing isn't accidentally positional. */
    @Test
    void message041A_nonContiguousOpcode_isRoutedAndParsed() {
        byte[] payload = payloadWithExtraField(MSG_041A_ID, 24, 555, 5, 12, 42);

        ObservedMessage message = pipeline.ingestForInterface(
                payload, "UDP", "127.0.0.1:9000", 1, interfaceConfig, registry);

        assertThat(message.parseError()).isNull();
        assertThat(message.messageType()).isEqualTo("Message041A");
        assertThat(message.body())
                .containsEntry("messageSourceId", 12)
                .containsEntry("payloadValue", 42);
    }

    private static byte[] headerOnlyPayload(
            int messageId, int messageLength, int messageTimeTag, int messageSequenceNumber, int messageSourceId) {
        ByteBuffer buffer = ByteBuffer.allocate(20);
        buffer.putInt(messageId);
        buffer.putInt(messageLength);
        buffer.putInt(messageTimeTag);
        buffer.putInt(messageSequenceNumber);
        buffer.putInt(messageSourceId);
        return buffer.array();
    }

    private static byte[] payloadWithExtraField(
            int messageId, int messageLength, int messageTimeTag, int messageSequenceNumber,
            int messageSourceId, int payloadValue) {
        ByteBuffer buffer = ByteBuffer.allocate(24);
        buffer.putInt(messageId);
        buffer.putInt(messageLength);
        buffer.putInt(messageTimeTag);
        buffer.putInt(messageSequenceNumber);
        buffer.putInt(messageSourceId);
        buffer.putInt(payloadValue);
        return buffer.array();
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
