package com.example.schemas.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherConditionTest {

    @ParameterizedTest
    @EnumSource(WeatherCondition.class)
    void fromCode_withKnownCode_returnsMatchingEnum(WeatherCondition value) {
        assertThat(WeatherCondition.fromCode(value.getCode())).isEqualTo(value);
    }

    @Test
    void fromCode_withUnknownCode_returnsUnknown() {
        assertThat(WeatherCondition.fromCode((byte) 99)).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(WeatherCondition.class)
    void fromWireName_withKnownNameCaseInsensitive_returnsMatchingEnum(WeatherCondition value) {
        assertThat(WeatherCondition.fromWireName(value.getWireName().toUpperCase())).isEqualTo(value);
    }

    @Test
    void fromWireName_withNullName_returnsUnknown() {
        assertThat(WeatherCondition.fromWireName(null)).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    void fromWireName_withUnrecognizedName_returnsUnknown() {
        assertThat(WeatherCondition.fromWireName("nonsense")).isEqualTo(WeatherCondition.UNKNOWN);
    }
}
