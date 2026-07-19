package com.example.schemas.ping;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PingMessageTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void toByteArray_thenFromByteBuffer_roundTripsSequence(int sequence) {
        PingMessage message = new PingMessage(sequence);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        message.toByteArray(buffer);

        PingMessage decoded = PingMessage.fromByteBuffer(ByteBuffer.wrap(buffer.array()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void toByteArray_exactByteLayout() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        new PingMessage(0x01020304).toByteArray(buffer);

        assertThat(buffer.array()).hasSize(Integer.BYTES);
        assertThat(ByteBuffer.wrap(buffer.array()).getInt()).isEqualTo(0x01020304);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Ping Interface", "Ping", 3001, PingMessage.class);

        byte[] body = definition.encodeBody(Map.of("sequence", 42));
        PingMessage decoded = (PingMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PingMessage(42));
        assertThat(definition.decodeBody(ByteBuffer.wrap(body))).containsEntry("sequence", 42);
    }
}
