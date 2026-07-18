package com.example.schemas.candy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandyProtocolCodecTest {

    private final CandyProtocolCodec codec = new CandyProtocolCodec();

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 1.0, -1.0, 250.5, Double.MAX_VALUE})
    void encodeCandy_thenDecode_roundTripsNameAndCalories(double calories) {
        byte[] payload = codec.encodeCandy(new CandyMessage("chocolate-bar", calories), 1L).payload();

        CandyProtocolCodec.DecodedCandyMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Candy");
        assertThat(decoded.bodyFields().get("name")).isEqualTo("chocolate-bar");
        assertThat(decoded.bodyFields().get("calories")).isEqualTo(calories);
    }

    @Test
    void decode_withUnknownOpcode_setsMessageTypeUnknown() {
        byte[] payload = com.example.schemacore.ProtocolHeaderCodec.encodeMessage(1, 1L, new byte[0]);

        CandyProtocolCodec.DecodedCandyMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Unknown");
        assertThat(decoded.bodyFields()).isEmpty();
    }

    @Test
    void decodeCandyBody_whenTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> CandyProtocolCodec.decodeCandyBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodeCandyBody_withInvalidNameLength_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Double.BYTES);
        buffer.putInt(100);
        buffer.putDouble(1.0);
        buffer.flip();

        assertThatThrownBy(() -> CandyProtocolCodec.decodeCandyBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid name length");
    }

    @Test
    void encodeCandyBody_exactByteLayout() {
        byte[] body = CandyProtocolCodec.encodeCandyBody(new CandyMessage("ab", 2.5));

        ByteBuffer buffer = ByteBuffer.wrap(body);
        assertThat(buffer.getInt()).isEqualTo(2);
        byte[] nameBytes = new byte[2];
        buffer.get(nameBytes);
        assertThat(new String(nameBytes)).isEqualTo("ab");
        assertThat(buffer.getDouble()).isEqualTo(2.5);
    }
}
