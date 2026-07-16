package com.example.schemacore;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolHeaderCodecTest {

    @Test
    void headerSizeBytesIsSixteen() {
        assertThat(ProtocolHeaderCodec.HEADER_SIZE_BYTES).isEqualTo(16);
    }

    @Test
    void encodeMessage_thenDecodeHeader_roundTripsOpcodeTimestampAndBodyLength() {
        byte[] body = {1, 2, 3, 4, 5};
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(42, 1_700_000_000_000L, body);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        assertThat(header.opcode()).isEqualTo(42);
        assertThat(header.sendTimeEpochMillis()).isEqualTo(1_700_000_000_000L);
        assertThat(header.bodyLength()).isEqualTo(body.length);
    }

    @Test
    void encodeMessage_producesExactlyHeaderSizePlusBodyLengthBytes() {
        byte[] body = {9, 8, 7};
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(7, 123L, body);

        assertThat(encoded).hasSize(ProtocolHeaderCodec.HEADER_SIZE_BYTES + body.length);
    }

    @Test
    void encodeMessage_exactByteLayout() {
        byte[] body = {0x11, 0x22};
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(0x01020304, 0x1122334455667788L, body);

        ByteBuffer buffer = ByteBuffer.wrap(encoded);
        assertThat(buffer.getInt(0)).isEqualTo(0x01020304);
        assertThat(buffer.getLong(4)).isEqualTo(0x1122334455667788L);
        assertThat(buffer.getInt(12)).isEqualTo(body.length);
        assertThat(encoded[16]).isEqualTo((byte) 0x11);
        assertThat(encoded[17]).isEqualTo((byte) 0x22);
    }

    @Test
    void encodeMessage_withEmptyBody_producesBodyLengthZero() {
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(1, 1L, new byte[0]);

        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(encoded));

        assertThat(header.bodyLength()).isZero();
        assertThat(encoded).hasSize(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
    }

    @Test
    void decodeHeader_whenBufferShorterThanHeaderSize_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(10);

        assertThatThrownBy(() -> ProtocolHeaderCodec.decodeHeader(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too short");
    }

    @Test
    void decodeHeader_whenExactlyHeaderSizeWithNoBody_succeedsWithBodyLengthZero() {
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(5, 5L, new byte[0]);

        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(encoded));

        assertThat(header.bodyLength()).isZero();
    }

    @Test
    void decodeHeader_whenBodyLengthFieldNegative_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
        buffer.putInt(1);
        buffer.putLong(1L);
        buffer.putInt(-1);
        buffer.flip();

        assertThatThrownBy(() -> ProtocolHeaderCodec.decodeHeader(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("negative");
    }

    @Test
    void decodeHeader_whenRemainingBytesFewerThanDeclaredBodyLength_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES + 1);
        buffer.putInt(1);
        buffer.putLong(1L);
        buffer.putInt(5);
        buffer.put((byte) 1);
        buffer.flip();

        assertThatThrownBy(() -> ProtocolHeaderCodec.decodeHeader(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid bodyLength");
    }

    @Test
    void decodeHeader_whenRemainingBytesMoreThanDeclaredBodyLength_throwsIllegalArgumentException() {
        ByteBuffer buffer = ByteBuffer.allocate(ProtocolHeaderCodec.HEADER_SIZE_BYTES + 5);
        buffer.putInt(1);
        buffer.putLong(1L);
        buffer.putInt(1);
        buffer.put(new byte[5]);
        buffer.flip();

        assertThatThrownBy(() -> ProtocolHeaderCodec.decodeHeader(buffer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid bodyLength");
    }

    @Test
    void decodeHeader_doesNotConsumeBodyBytes() {
        byte[] body = {1, 2, 3};
        byte[] encoded = ProtocolHeaderCodec.encodeMessage(1, 1L, body);
        ByteBuffer buffer = ByteBuffer.wrap(encoded);

        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        assertThat(buffer.remaining()).isEqualTo(header.bodyLength());
    }
}
