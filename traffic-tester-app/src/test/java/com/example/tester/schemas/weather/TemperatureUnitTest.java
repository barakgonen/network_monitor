package com.example.tester.schemas.weather;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemperatureUnitTest {

    @ParameterizedTest
    @EnumSource(TemperatureUnit.class)
    void fromCode_withKnownCode_returnsMatchingEnum(TemperatureUnit value) {
        assertThat(TemperatureUnit.fromCode(value.getCode())).isEqualTo(value);
    }

    @Test
    void fromCode_withUnknownCode_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TemperatureUnit.fromCode((byte) 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
    }

    @ParameterizedTest
    @EnumSource(TemperatureUnit.class)
    void fromWireName_withKnownNameCaseInsensitive_returnsMatchingEnum(TemperatureUnit value) {
        assertThat(TemperatureUnit.fromWireName(value.name().toLowerCase())).isEqualTo(value);
    }

    @Test
    void fromWireName_withNullName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TemperatureUnit.fromWireName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromWireName_withUnrecognizedName_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> TemperatureUnit.fromWireName("kelvin"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
