package com.example.schemas.weather;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherOpcodesTest {

    @Test
    void messageType_forKnownOpcode_returnsExpectedName() {
        assertThat(WeatherOpcodes.messageType(WeatherOpcodes.TEMPERATURE_READING)).isEqualTo("TemperatureReading");
    }

    @Test
    void messageType_forUnknownOpcode_returnsUnknown() {
        assertThat(WeatherOpcodes.messageType(-1)).isEqualTo("Unknown");
    }
}
