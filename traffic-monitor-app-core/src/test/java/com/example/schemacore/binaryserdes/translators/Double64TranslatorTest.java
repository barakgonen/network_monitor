package com.example.schemacore.binaryserdes.translators;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Double64TranslatorTest {

    @Test
    void roundTripShouldPreserveDouble() {
        Double64Translator t = new Double64Translator();

        double original = 1234.5678d;

        ByteBuffer buf = ByteBuffer.allocate(8);
        t.toBytes(original, buf);

        assertThat(buf.remaining()).isZero();

        buf.flip();
        double decoded = t.fromBytes(buf);

        assertThat(decoded).isEqualTo(original);
        assertThat(buf.remaining()).isZero();
    }

    @Test
    void roundTripSpecialValues() {
        Double64Translator t = new Double64Translator();

        double[] values = {0.0, -0.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};

        for (double v : values) {
            ByteBuffer buf = ByteBuffer.allocate(8);
            t.toBytes(v, buf);
            buf.flip();
            double decoded = t.fromBytes(buf);

            if (Double.isNaN(v)) {
                assertThat(Double.isNaN(decoded)).isTrue();
            } else {
                assertThat(decoded).isEqualTo(v);
            }
        }
    }

    @Test
    void toBytesShouldRejectNull() {
        Double64Translator t = new Double64Translator();
        ByteBuffer buf = ByteBuffer.allocate(8);

        assertThatThrownBy(() -> t.toBytes(null, buf)).isInstanceOf(IllegalArgumentException.class);
    }
}
