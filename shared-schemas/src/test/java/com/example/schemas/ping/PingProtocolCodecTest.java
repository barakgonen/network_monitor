package com.example.schemas.ping;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PingProtocolCodecTest {

    private final PingProtocolCodec codec = new PingProtocolCodec();

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void encodePing_thenDecode_roundTripsSequence(int sequence) {
        byte[] payload = codec.encodePing(new PingMessage(sequence), 1L).payload();

        PingProtocolCodec.DecodedPingMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Ping");
        assertThat(decoded.bodyFields().get("sequence")).isEqualTo(sequence);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void encodePong_thenDecode_roundTripsSequence(int sequence) {
        byte[] payload = codec.encodePong(new PongMessage(sequence), 1L).payload();

        PingProtocolCodec.DecodedPingMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Pong");
        assertThat(decoded.bodyFields().get("sequence")).isEqualTo(sequence);
    }

    @Test
    void decode_withUnknownOpcode_setsMessageTypeUnknown() {
        byte[] payload = com.example.schemacore.ProtocolHeaderCodec.encodeMessage(1, 1L, new byte[0]);

        PingProtocolCodec.DecodedPingMessage decoded = codec.decode(payload);

        assertThat(decoded.messageType()).isEqualTo("Unknown");
        assertThat(decoded.bodyFields()).isEmpty();
    }

    @Test
    void decodePingBody_whenTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> PingProtocolCodec.decodePingBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodePongBody_whenTooShort_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> PingProtocolCodec.decodePongBody(buffer, new LinkedHashMap<>()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void encodePingBody_exactByteLayout() {
        byte[] body = PingProtocolCodec.encodePingBody(new PingMessage(0x01020304));

        assertThat(body).hasSize(Integer.BYTES);
        assertThat(ByteBuffer.wrap(body).getInt()).isEqualTo(0x01020304);
    }
}
