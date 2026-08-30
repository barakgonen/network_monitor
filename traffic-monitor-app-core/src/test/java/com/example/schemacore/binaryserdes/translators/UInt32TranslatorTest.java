package com.example.schemacore.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UInt32TranslatorTest {

    @Test
    void fromBytesReadsUnsignedInt() {
        UInt32Translator t = new UInt32Translator();

        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(0xFFFFFFFF);
        buf.flip();

        long val = t.fromBytes(buf);

        assertThat(val).isEqualTo(4294967295L);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void toBytesWritesUnsignedInt() {
        UInt32Translator t = new UInt32Translator();

        ByteBuffer buf = ByteBuffer.allocate(4);
        t.toBytes(4294967295L, buf);

        assertThat(buf.remaining()).isZero();
        buf.flip();

        int stored = buf.getInt();
        assertThat(stored).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void toBytesRejectsOutOfRange() {
        UInt32Translator t = new UInt32Translator();
        ByteBuffer buf = ByteBuffer.allocate(4);

        assertThatThrownBy(() -> t.toBytes(-1L, buf)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.toBytes(5000000000L, buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
