package com.example.monitor.publishing;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ProtocolMessage;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MonitorPayloadFactoryTest {

    @Test
    void create_fromFieldsMap_encodesUsingMatchingDefinition() {
        MonitorPayloadFactory factory = new MonitorPayloadFactory(
                new MessageDefinitionRegistry(List.of(new StubDefinition())));

        byte[] payload = factory.create("Stub Interface", "Stub", Map.of("value", "hello"));

        assertThat(payload).hasSize(ProtocolHeaderCodec.HEADER_SIZE_BYTES + "hello".getBytes().length);
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        assertThat(buffer.getInt(0)).isEqualTo(9001);
    }

    @Test
    void create_fromFieldsMap_whenInterfaceOrMessageTypeUnknown_throwsIllegalArgumentException() {
        MonitorPayloadFactory factory = new MonitorPayloadFactory(new MessageDefinitionRegistry(List.of()));

        assertThatThrownBy(() -> factory.create("Unknown Interface", "Unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported message");
    }

    @Test
    void create_fromFieldsMap_whenEncodeBodyThrows_wrapsInIllegalArgumentException() {
        MonitorPayloadFactory factory = new MonitorPayloadFactory(
                new MessageDefinitionRegistry(List.of(new StubDefinition())));

        assertThatThrownBy(() -> factory.create("Stub Interface", "Stub", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to encode message");
    }

    @Test
    void create_fromProtocolMessageInstance_findsDefinitionByMessageClassAndEncodes() {
        MonitorPayloadFactory factory = new MonitorPayloadFactory(
                new MessageDefinitionRegistry(List.of(new StubDefinition())));

        byte[] payload = factory.create(new StubMessage("world"));

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        assertThat(buffer.getInt(0)).isEqualTo(9001);
    }

    @Test
    void create_fromProtocolMessageInstance_whenClassNotRegistered_throwsIllegalArgumentException() {
        MonitorPayloadFactory factory = new MonitorPayloadFactory(new MessageDefinitionRegistry(List.of()));

        assertThatThrownBy(() -> factory.create(new StubMessage("world")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No MessageDefinition registered");
    }

    private record StubMessage(String value) implements ProtocolMessage {
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
            return 9001;
        }

        @Override
        public Class<? extends ProtocolMessage> messageClass() {
            return StubMessage.class;
        }

        @Override
        public Map<String, Object> decodeBody(ByteBuffer body) {
            return Map.of();
        }

        @Override
        public ProtocolMessage decodeMessage(ByteBuffer body) {
            return null;
        }

        @Override
        public byte[] encodeBody(Map<String, Object> fields) {
            if (!fields.containsKey("value")) {
                throw new IllegalStateException("missing value");
            }
            return String.valueOf(fields.get("value")).getBytes();
        }

        @Override
        public byte[] encodeBody(ProtocolMessage message) {
            return ((StubMessage) message).value().getBytes();
        }
    }
}
