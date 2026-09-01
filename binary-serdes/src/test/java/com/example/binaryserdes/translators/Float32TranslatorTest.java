package com.example.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class Float32TranslatorTest {

    @Test
    void roundTripsPositiveAndNegativeValues() {
        Float32Translator t = new Float32Translator();

        ByteBuffer buf = ByteBuffer.allocate(8);
        t.toBytes(12.5f, buf);
        t.toBytes(-3.25f, buf);
        buf.flip();

        assertThat(t.fromBytes(buf)).isEqualTo(12.5f);
        assertThat(t.fromBytes(buf)).isEqualTo(-3.25f);
        assertThat(buf.remaining()).isZero();
    }
}
