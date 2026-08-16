package com.example.monitor.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageConfigTest {

    @Test
    void byteOrder_defaultsToNull_meaningInheritFromInterface() {
        MessageConfig message = new MessageConfig();

        assertThat(message.getByteOrder()).isNull();
    }

    @Test
    void byteOrder_roundTripsThroughSetter() {
        MessageConfig message = new MessageConfig();

        message.setByteOrder("LITTLE_ENDIAN");

        assertThat(message.getByteOrder()).isEqualTo("LITTLE_ENDIAN");
    }
}
