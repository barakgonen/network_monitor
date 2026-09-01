package com.example.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedStringTranslatorTest {

    @Test
    void fromBytesReadsAndTrimsNulls() {
        FixedStringTranslator t = new FixedStringTranslator(5);

        ByteBuffer buf = ByteBuffer.allocate(5);
        buf.put("BG".getBytes(StandardCharsets.US_ASCII));
        buf.put((byte) 0x00);
        buf.put((byte) 0x00);
        buf.put((byte) 0x00);
        buf.flip();

        String value = t.fromBytes(buf);

        assertThat(value).isEqualTo("BG");
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void toBytesWritesAndPads() {
        FixedStringTranslator t = new FixedStringTranslator(5);

        ByteBuffer buf = ByteBuffer.allocate(5);
        t.toBytes("BG", buf);

        assertThat(buf.remaining()).isZero();
        buf.flip();

        byte[] out = new byte[5];
        buf.get(out);

        assertThat(out).containsExactly('B', 'G', 0x00, 0x00, 0x00);
    }

    @Test
    void toBytesRejectsTooLong() {
        FixedStringTranslator t = new FixedStringTranslator(5);
        ByteBuffer buf = ByteBuffer.allocate(5);

        assertThatThrownBy(() -> t.toBytes("123456", buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
