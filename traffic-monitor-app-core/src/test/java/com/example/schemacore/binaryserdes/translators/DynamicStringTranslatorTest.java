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
        byte[] arr = new byte[2 + original.length()];
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

        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.putShort((short) 10); // length=10, but only 2 bytes remain
        buf.putShort((short) 0x1234);
        buf.flip();

        assertThatThrownBy(() -> t.fromBytes(buf)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void toBytesRejectsTooLong() {
        DynamicStringTranslator t = new DynamicStringTranslator();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70000; i++) {
            sb.append('a');
        }

        ByteBuffer buf = ByteBuffer.allocate(2 + 70000);
        assertThatThrownBy(() -> t.toBytes(sb.toString(), buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
