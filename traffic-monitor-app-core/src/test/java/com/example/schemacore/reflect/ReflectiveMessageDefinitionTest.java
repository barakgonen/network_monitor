package com.example.schemacore.reflect;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ReflectiveMessageDefinitionTest {

    enum Freshness {
        FRESH,
        STALE
    }

    record TestMessage(int id, Freshness freshness) {
        static TestMessage fromByteBuffer(ByteBuffer buffer) {
            int idValue = buffer.getInt();
            Freshness freshnessValue = Freshness.values()[buffer.getInt()];
            return new TestMessage(idValue, freshnessValue);
        }

        void toByteArray(ByteBuffer buffer) {
            buffer.putInt(id);
            buffer.putInt(freshness.ordinal());
        }
    }

    private final ReflectiveMessageDefinition definition =
            new ReflectiveMessageDefinition("Test Interface", "TestMessage", 55, TestMessage.class);

    @Test
    void exposesRegistrationMetadata() {
        assertThat(definition.interfaceName()).isEqualTo("Test Interface");
        assertThat(definition.messageType()).isEqualTo("TestMessage");
        assertThat(definition.opcode()).isEqualTo(55);
        assertThat(definition.messageClass()).isEqualTo(TestMessage.class);
    }

    @Test
    void decodeMessage_thenEncodeBody_roundTrips() throws Exception {
        TestMessage original = new TestMessage(3, Freshness.STALE);
        byte[] encoded = definition.encodeBody(original);

        Object decoded = definition.decodeMessage(ByteBuffer.wrap(encoded));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void decodeBody_extractsGenericFieldMap() throws Exception {
        TestMessage original = new TestMessage(9, Freshness.FRESH);
        byte[] encoded = definition.encodeBody(original);

        Map<String, Object> fields = definition.decodeBody(ByteBuffer.wrap(encoded));

        assertThat(fields).containsEntry("id", 9).containsEntry("freshness", "FRESH");
    }

    @Test
    void encodeBody_fromFieldMap_buildsAndEncodesMessage() throws Exception {
        Map<String, Object> fields = Map.of("id", 12, "freshness", "STALE");

        byte[] encoded = definition.encodeBody(fields);

        assertThat(definition.decodeMessage(ByteBuffer.wrap(encoded)))
                .isEqualTo(new TestMessage(12, Freshness.STALE));
    }

    @Test
    void decodeMessage_thenEncodeBody_roundTrips_withConfiguredByteOrder() throws Exception {
        ReflectiveMessageDefinition littleEndianDefinition = new ReflectiveMessageDefinition(
                "Test Interface", "TestMessage", 55, TestMessage.class, ByteOrder.LITTLE_ENDIAN);

        TestMessage original = new TestMessage(3, Freshness.STALE);
        byte[] encoded = littleEndianDefinition.encodeBody(original);

        assertThat(ByteBuffer.wrap(encoded).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(3);
        assertThat(littleEndianDefinition.decodeMessage(ByteBuffer.wrap(encoded))).isEqualTo(original);
    }
}
