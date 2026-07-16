package com.example.schemas.fruit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FruitOpcodesTest {

    @Test
    void messageType_forKnownOpcodes_returnsExpectedNames() {
        assertThat(FruitOpcodes.messageType(FruitOpcodes.ORANGE)).isEqualTo("Orange");
        assertThat(FruitOpcodes.messageType(FruitOpcodes.BANANA)).isEqualTo("Banana");
    }

    @Test
    void messageType_forUnknownOpcode_returnsUnknown() {
        assertThat(FruitOpcodes.messageType(-1)).isEqualTo("Unknown");
    }
}
