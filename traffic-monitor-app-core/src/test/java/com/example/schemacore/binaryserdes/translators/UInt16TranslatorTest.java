package com.example.schemacore.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UInt16TranslatorTest {

    @Test
    void fromBytesReadsUnsignedShort() {
        UInt16Translator t = new UInt16Translator();

        ByteBuffer buf = ByteBuffer.allocate(2);
        buf.putShort((short) 0xFFFF);
        buf.flip();

        int val = t.fromBytes(buf);

        assertThat(val).isEqualTo(65535);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void toBytesWritesUnsignedShort() {
        UInt16Translator t = new UInt16Translator();

        ByteBuffer buf = ByteBuffer.allocate(2);
        t.toBytes(65535, buf);

        assertThat(buf.remaining()).isZero();
        buf.flip();

        short stored = buf.getShort();
        assertThat(stored).isEqualTo((short) 0xFFFF);
    }

    @Test
    void toBytesRejectsOutOfRange() {
        UInt16Translator t = new UInt16Translator();
        ByteBuffer buf = ByteBuffer.allocate(2);

        assertThatThrownBy(() -> t.toBytes(-1, buf)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> t.toBytes(70000, buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
