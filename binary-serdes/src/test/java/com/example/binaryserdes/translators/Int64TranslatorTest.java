package com.example.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class Int64TranslatorTest {

    @Test
    void roundTripsLargeValues() {
        Int64Translator t = new Int64Translator();

        ByteBuffer buf = ByteBuffer.allocate(8);
        t.toBytes(9_223_372_036_854_775_807L, buf);
        buf.flip();

        assertThat(t.fromBytes(buf)).isEqualTo(9_223_372_036_854_775_807L);
        assertThat(buf.remaining()).isZero();
    }
}
