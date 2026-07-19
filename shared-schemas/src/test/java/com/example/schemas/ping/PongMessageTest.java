package com.example.schemas.ping;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PongMessageTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void toByteArray_thenFromByteBuffer_roundTripsSequence(int sequence) {
        PongMessage message = new PongMessage(sequence);
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        message.toByteArray(buffer);

        PongMessage decoded = PongMessage.fromByteBuffer(ByteBuffer.wrap(buffer.array()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void toByteArray_exactByteLayout() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        new PongMessage(0x01020304).toByteArray(buffer);

        assertThat(buffer.array()).hasSize(Integer.BYTES);
        assertThat(ByteBuffer.wrap(buffer.array()).getInt()).isEqualTo(0x01020304);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Ping Interface", "Pong", 3002, PongMessage.class);

        byte[] body = definition.encodeBody(Map.of("sequence", 42));
        PongMessage decoded = (PongMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new PongMessage(42));
        assertThat(definition.decodeBody(ByteBuffer.wrap(body))).containsEntry("sequence", 42);
    }
}
