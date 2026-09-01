package com.example.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UInt8TranslatorTest {

    @Test
    void fromBytesReadsUnsignedByte() {
        UInt8Translator t = new UInt8Translator();

        ByteBuffer buf = ByteBuffer.allocate(1);
        buf.put((byte) 0xFF);
        buf.flip();

        int val = t.fromBytes(buf);

        assertThat(val).isEqualTo(255);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void toBytesWritesUnsignedByte() {
        UInt8Translator t = new UInt8Translator();

        ByteBuffer buf = ByteBuffer.allocate(1);
        t.toBytes(255, buf);

        assertThat(buf.remaining()).isZero();
        buf.flip();

        assertThat(buf.get()).isEqualTo((byte) 0xFF);
    }

    @Test
    void toBytesRejectsOutOfRange() {
        UInt8Translator t = new UInt8Translator();
        ByteBuffer buf = ByteBuffer.allocate(1);

        assertThatThrownBy(() -> t.toBytes(-1, buf)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.toBytes(256, buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
