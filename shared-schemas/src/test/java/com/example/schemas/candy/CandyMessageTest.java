package com.example.schemas.candy;

import com.example.schemacore.ReflectiveMessageDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandyMessageTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -1.0, 250.5, Double.MAX_VALUE})
    void toByteArray_thenFromByteBuffer_roundTripsNameAndCalories(double calories) {
        CandyMessage message = new CandyMessage("chocolate-bar", calories);

        CandyMessage decoded = CandyMessage.fromByteBuffer(ByteBuffer.wrap(message.toByteArray()));

        assertThat(decoded).isEqualTo(message);
    }

    @Test
    void fromByteBuffer_whenTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> CandyMessage.fromByteBuffer(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void fromByteBuffer_withInvalidNameLength_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES);
        buffer.putInt(100);
        buffer.putDouble(1.0);
        buffer.flip();

        assertThatThrownBy(() -> CandyMessage.fromByteBuffer(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid name length");
    }

    @Test
    void toByteArray_exactByteLayout() {
        byte[] body = new CandyMessage("ab", 2.5).toByteArray();

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] nameBytes = new byte[2];
        buffer.get(nameBytes);
        assertThat(new String(nameBytes)).isEqualTo("ab");
        assertThat(buffer.getDouble()).isEqualTo(2.5);
    }

    @Test
    void reflectiveMessageDefinition_decodesAndEncodesConsistently() throws Exception {
        ReflectiveMessageDefinition definition =
                new ReflectiveMessageDefinition("Candy Interface", "Candy", 4001, CandyMessage.class);

        byte[] body = definition.encodeBody(Map.of("name", "lollipop", "calories", 80.0));
        CandyMessage decoded = (CandyMessage) definition.decodeMessage(ByteBuffer.wrap(body));

        assertThat(decoded).isEqualTo(new CandyMessage("lollipop", 80.0));
        assertThat(definition.decodeBody(ByteBuffer.wrap(body)))
                .containsEntry("name", "lollipop")
                .containsEntry("calories", 80.0);
    }
}
