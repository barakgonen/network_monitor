package com.example.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ByteTranslatorTest {

    @Test
    void fromBytesReadsSignedByte() {
        ByteTranslator t = new ByteTranslator();

        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte) 0xFF);
        buf.flip();

        byte val = t.fromBytes(buf);

        assertThat(val).isEqualTo((byte) -1);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void toBytesWritesSignedByte() {
        ByteTranslator t = new ByteTranslator();

        ByteBuffer buf = ByteBuffer.allocate(1);
        t.toBytes((byte) -1, buf);

        assertThat(buf.remaining()).isZero();
        buf.flip();

        assertThat(buf.get()).isEqualTo((byte) 0xFF);
    }

    @Test
    void toBytesRejectsNull() {
        ByteTranslator t = new ByteTranslator();
        ByteBuffer buf = ByteBuffer.allocate(1);

        assertThatThrownBy(() -> t.toBytes(null, buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
