package com.example.schemas.fruit;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BananaMessageTest {

    @Test
    void toByteArray_thenFromByteBuffer_roundTripsColorAndWeight() {
        BananaMessage message = new BananaMessage("yellow", 123.456);

        BananaMessage decoded = BananaMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void fromByteBuffer_whenTooShort_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> BananaMessage.fromByteBuffer(ByteBuffer.allocate(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void toByteArray_exactByteLayout() {
        byte[] body = new BananaMessage("ab", 2.5).toByteArray();

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] colorBytes = new byte[2];
        buffer.get(colorBytes);
        assertThat(new String(colorBytes)).isEqualTo("ab");
        assertThat(buffer.getDouble()).isEqualTo(2.5);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Fruit Interface", "Banana", 1002, BananaMessage.class);

        byte[] body = definition.encodeBody(Map.of("color", "yellow", "weight", 123.456));
        BananaMessage decoded = (BananaMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new BananaMessage("yellow", 123.456));
    }
}
