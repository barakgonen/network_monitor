package com.example.monitor.schema;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolMessage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageSchemaWiringConfigTest {

    /** Order-sensitive fixture: a single int field, decoded/encoded differently depending on byte order. */
    public record StubOrderSensitiveMessage(int value) implements ProtocolMessage {
        public static StubOrderSensitiveMessage fromByteBuffer(ByteBuffer buffer) {
            return new StubOrderSensitiveMessage(buffer.getInt());
        }

        public void toByteArray(ByteBuffer buffer) {
            buffer.putInt(value);
        }
    }

    private final MessageSchemaWiringConfig wiring = new MessageSchemaWiringConfig();

    private InterfaceConfig interfaceConfig(String byteOrder, MessageConfig... messages) {
        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey("stub");
        interfaceConfig.setName("Stub Interface");
        interfaceConfig.setPort(1);
        if (byteOrder != null) {
            interfaceConfig.setByteOrder(byteOrder);
        }
        interfaceConfig.setMessages(List.of(messages));
        return interfaceConfig;
    }

    private MessageConfig messageConfig(String byteOrder) {
        MessageConfig message = new MessageConfig();
        message.setType("StubOrderSensitiveMessage");
        message.setMessageClass(StubOrderSensitiveMessage.class.getName());
        message.setOpcode(1);
        message.setByteOrder(byteOrder);
        return message;
    }

    private TrafficToolConfig trafficToolConfig(InterfaceConfig... interfaces) {
        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(interfaces));
        return config;
    }

    @Test
    void messageByteOrder_overridesInterfaceDefault() throws Exception {
        InterfaceConfig config = interfaceConfig("BIG_ENDIAN", messageConfig("LITTLE_ENDIAN"));
        MessageDefinitionRegistry registry =
                wiring.messageDefinitionRegistry(trafficToolConfig(config));
        MessageDefinition definition = registry.findByOpcode(1).orElseThrow();

        byte[] encoded = definition.encodeBody(new StubOrderSensitiveMessage(258));

        assertThat(encoded).containsExactly(0x02, 0x01, 0x00, 0x00);
    }

    @Test
    void messageByteOrder_fallsBackToInterfaceDefault_whenUnset() throws Exception {
        InterfaceConfig config = interfaceConfig("LITTLE_ENDIAN", messageConfig(null));
        MessageDefinitionRegistry registry =
                wiring.messageDefinitionRegistry(trafficToolConfig(config));
        MessageDefinition definition = registry.findByOpcode(1).orElseThrow();

        byte[] encoded = definition.encodeBody(new StubOrderSensitiveMessage(258));

        assertThat(encoded).containsExactly(0x02, 0x01, 0x00, 0x00);
    }

    @Test
    void byteOrder_defaultsToBigEndian_whenNeitherLevelConfiguresIt() throws Exception {
        InterfaceConfig config = interfaceConfig(null, messageConfig(null));
        MessageDefinitionRegistry registry =
                wiring.messageDefinitionRegistry(trafficToolConfig(config));
        MessageDefinition definition = registry.findByOpcode(1).orElseThrow();

        byte[] encoded = definition.encodeBody(new StubOrderSensitiveMessage(258));

        assertThat(encoded).containsExactly(0x00, 0x00, 0x01, 0x02);
    }

    @Test
    void invalidByteOrder_failsFastWithClearMessage() {
        InterfaceConfig config = interfaceConfig(null, messageConfig("MIDDLE_ENDIAN"));

        assertThatThrownBy(() -> wiring.messageDefinitionRegistry(trafficToolConfig(config)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIDDLE_ENDIAN")
                .hasMessageContaining("BIG_ENDIAN or LITTLE_ENDIAN");
    }
}
