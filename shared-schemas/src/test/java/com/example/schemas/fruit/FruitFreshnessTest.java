package com.example.schemas.fruit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class FruitFreshnessTest {

    @ParameterizedTest
    @EnumSource(FruitFreshness.class)
    void fromCode_withKnownCode_returnsMatchingEnum(FruitFreshness value) {
        assertThat(FruitFreshness.fromCode(value.getCode())).isEqualTo(value);
    }

    @Test
    void fromCode_withUnknownCode_returnsUnknown() {
        assertThat(FruitFreshness.fromCode((byte) 99)).isEqualTo(FruitFreshness.UNKNOWN);
    }

    @ParameterizedTest
    @EnumSource(FruitFreshness.class)
    void fromWireName_withKnownNameCaseInsensitive_returnsMatchingEnum(FruitFreshness value) {
        assertThat(FruitFreshness.fromWireName(value.getWireName().toUpperCase())).isEqualTo(value);
    }

    @Test
    void fromWireName_withNullName_returnsUnknown() {
        assertThat(FruitFreshness.fromWireName(null)).isEqualTo(FruitFreshness.UNKNOWN);
    }

    @Test
    void fromWireName_withUnrecognizedName_returnsUnknown() {
        assertThat(FruitFreshness.fromWireName("nonsense")).isEqualTo(FruitFreshness.UNKNOWN);
    }
}
