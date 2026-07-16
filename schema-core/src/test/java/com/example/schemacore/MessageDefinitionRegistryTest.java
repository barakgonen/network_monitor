package com.example.schemacore;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageDefinitionRegistryTest {

    @Test
    void constructor_withUniqueDefinitions_indexesAllByOpcodeInterfaceTypeAndClass() {
        StubDefinition orange = new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class);
        StubDefinition banana = new StubDefinition("Fruit Interface", "Banana", 2, OtherStubMessage.class);

        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(List.of(orange, banana));

        assertThat(registry.findByOpcode(1)).contains(orange);
        assertThat(registry.findByOpcode(2)).contains(banana);
        assertThat(registry.find("Fruit Interface", "Orange")).contains(orange);
        assertThat(registry.find("Fruit Interface", "Banana")).contains(banana);
        assertThat(registry.findByMessageClass(StubMessage.class)).contains(orange);
        assertThat(registry.findByMessageClass(OtherStubMessage.class)).contains(banana);
    }

    @Test
    void constructor_withDuplicateOpcode_throwsIllegalStateException() {
        StubDefinition first = new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class);
        StubDefinition second = new StubDefinition("Weather Interface", "TemperatureReading", 1, OtherStubMessage.class);

        assertThatThrownBy(() -> new MessageDefinitionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("opcode");
    }

    @Test
    void constructor_withDuplicateInterfaceAndMessageTypeKey_throwsIllegalStateException() {
        StubDefinition first = new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class);
        StubDefinition second = new StubDefinition("Fruit Interface", "Orange", 2, OtherStubMessage.class);

        assertThatThrownBy(() -> new MessageDefinitionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fruit Interface::Orange");
    }

    @Test
    void constructor_withDuplicateMessageClass_throwsIllegalStateException() {
        StubDefinition first = new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class);
        StubDefinition second = new StubDefinition("Weather Interface", "TemperatureReading", 2, StubMessage.class);

        assertThatThrownBy(() -> new MessageDefinitionRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("message class");
    }

    @Test
    void constructor_withEmptyList_producesEmptyRegistry() {
        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(List.of());

        assertThat(registry.findByOpcode(1)).isEmpty();
        assertThat(registry.find("Fruit Interface", "Orange")).isEmpty();
        assertThat(registry.findByMessageClass(StubMessage.class)).isEmpty();
    }

    @Test
    void findByOpcode_whenNotFound_returnsEmptyOptional() {
        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(
                List.of(new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class)));

        assertThat(registry.findByOpcode(999)).isEmpty();
    }

    @Test
    void find_whenInterfaceNameMatchesButMessageTypeDoesNot_returnsEmptyOptional() {
        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(
                List.of(new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class)));

        assertThat(registry.find("Fruit Interface", "Banana")).isEmpty();
    }

    @Test
    void findByMessageClass_whenNotFound_returnsEmptyOptional() {
        MessageDefinitionRegistry registry = new MessageDefinitionRegistry(
                List.of(new StubDefinition("Fruit Interface", "Orange", 1, StubMessage.class)));

        assertThat(registry.findByMessageClass(OtherStubMessage.class)).isEmpty();
    }

    @Test
    void loadFromClassNames_withValidClassNames_instantiatesAndRegistersEachDefinition() throws Exception {
        MessageDefinitionRegistry registry = MessageDefinitionRegistry.loadFromClassNames(
                List.of(ValidStubDefinition.class.getName()));

        assertThat(registry.find("Stub Interface", "Stub")).isPresent();
    }

    @Test
    void loadFromClassNames_withEmptyList_returnsEmptyRegistry() throws Exception {
        MessageDefinitionRegistry registry = MessageDefinitionRegistry.loadFromClassNames(List.of());

        assertThat(registry.findByOpcode(1)).isEmpty();
    }

    @Test
    void loadFromClassNames_withNonExistentClassName_throwsReflectiveOperationException() {
        assertThatThrownBy(() -> MessageDefinitionRegistry.loadFromClassNames(List.of("com.example.DoesNotExist")))
                .isInstanceOf(ReflectiveOperationException.class);
    }

    @Test
    void loadFromClassNames_withClassMissingNoArgConstructor_throwsReflectiveOperationException() {
        assertThatThrownBy(() -> MessageDefinitionRegistry.loadFromClassNames(
                List.of(NoNoArgConstructorDefinition.class.getName())))
                .isInstanceOf(ReflectiveOperationException.class);
    }

    @Test
    void loadFromClassNames_withClassNotImplementingMessageDefinition_throwsClassCastException() {
        assertThatThrownBy(() -> MessageDefinitionRegistry.loadFromClassNames(
                List.of(NotAMessageDefinition.class.getName())))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void loadFromClassNames_withAbstractClass_throwsReflectiveOperationException() {
        assertThatThrownBy(() -> MessageDefinitionRegistry.loadFromClassNames(
                List.of(AbstractStubDefinition.class.getName())))
                .isInstanceOf(ReflectiveOperationException.class);
    }

    private static final class StubMessage implements ProtocolMessage {
    }

    private static final class OtherStubMessage implements ProtocolMessage {
    }

    private record StubDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            Class<? extends ProtocolMessage> messageClass
    ) implements MessageDefinition {
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
            return new byte[0];
        }

        @Override
        public byte[] encodeBody(ProtocolMessage message) {
            return new byte[0];
        }
    }

    public static final class ValidStubDefinition implements MessageDefinition {
        public ValidStubDefinition() {
        }

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
            return 12345;
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
            return new byte[0];
        }

        @Override
        public byte[] encodeBody(ProtocolMessage message) {
            return new byte[0];
        }
    }

    public abstract static class AbstractStubDefinition implements MessageDefinition {
    }

    public static final class NoNoArgConstructorDefinition implements MessageDefinition {
        public NoNoArgConstructorDefinition(String unused) {
        }

        @Override
        public String interfaceName() {
            return "Stub Interface";
        }

        @Override
        public String messageType() {
            return "Stub2";
        }

        @Override
        public int opcode() {
            return 54321;
        }

        @Override
        public Class<? extends ProtocolMessage> messageClass() {
            return OtherStubMessage.class;
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
            return new byte[0];
        }

        @Override
        public byte[] encodeBody(ProtocolMessage message) {
            return new byte[0];
        }
    }

    public static final class NotAMessageDefinition {
        public NotAMessageDefinition() {
        }
    }
}
