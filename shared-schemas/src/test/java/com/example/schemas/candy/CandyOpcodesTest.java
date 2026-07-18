package com.example.schemas.candy;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CandyOpcodesTest {

    @Test
    void messageType_forKnownOpcode_returnsExpectedName() {
        assertThat(CandyOpcodes.messageType(CandyOpcodes.CANDY)).isEqualTo("Candy");
    }

    @Test
    void messageType_forUnknownOpcode_returnsUnknown() {
        assertThat(CandyOpcodes.messageType(-1)).isEqualTo("Unknown");
    }
}
