package com.example.schemacore.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DynamicStringTranslatorTest {

    @Test
    void roundTripWorks() {
        DynamicStringTranslator t = new DynamicStringTranslator();

        String original = "hello world";
        byte[] arr = new byte[4 + original.length()];
        ByteBuffer buf = ByteBuffer.wrap(arr);

        t.toBytes(original, buf);
        assertThat(buf.remaining()).isZero();

        buf.flip();
        String decoded = t.fromBytes(buf);

        assertThat(decoded).isEqualTo(original);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void fromBytesRejectsInvalidLength() {
        DynamicStringTranslator t = new DynamicStringTranslator();

        ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putInt(10); // length=10, but only 4 bytes remain
        buf.putInt(0x12345678);
        buf.flip();

        assertThatThrownBy(() -> t.fromBytes(buf)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fromBytesRejectsNegativeLength() {
        DynamicStringTranslator t = new DynamicStringTranslator();

        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putInt(-1);
        buf.flip();

        assertThatThrownBy(() -> t.fromBytes(buf)).isInstanceOf(IllegalStateException.class);
    }
}
