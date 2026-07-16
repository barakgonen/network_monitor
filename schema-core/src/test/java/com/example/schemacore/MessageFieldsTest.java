package com.example.schemacore;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageFieldsTest {

    @Test
    void requireString_withPresentNonBlankValue_returnsString() {
        assertThat(MessageFields.requireString(Map.of("name", "farm-1"), "name")).isEqualTo("farm-1");
    }

    @Test
    void requireString_withNullMap_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireString(null, "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field");
    }

    @Test
    void requireString_withMissingKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireString(Map.of(), "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required field");
    }

    @Test
    void requireString_withBlankValue_throwsIllegalArgumentException() {
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", "   ");

        assertThatThrownBy(() -> MessageFields.requireString(fields, "name"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    void requireString_withNonStringValue_convertsViaStringValueOf() {
        assertThat(MessageFields.requireString(Map.of("count", 42), "count")).isEqualTo("42");
    }

    @Test
    void requireDouble_withNumberInstance_returnsDoubleValue() {
        assertThat(MessageFields.requireDouble(Map.of("weight", 7), "weight")).isEqualTo(7.0);
    }

    @Test
    void requireDouble_withParsableStringValue_returnsParsedDouble() {
        assertThat(MessageFields.requireDouble(Map.of("weight", "3.14"), "weight")).isEqualTo(3.14);
    }

    @Test
    void requireDouble_withUnparsableStringValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireDouble(Map.of("weight", "not-a-number"), "weight"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid double");
    }

    @Test
    void requireDouble_withNullMap_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireDouble(null, "weight"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireDouble_withMissingKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireDouble(Map.of(), "weight"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireInt_withNumberInstance_returnsIntValue() {
        assertThat(MessageFields.requireInt(Map.of("sequence", 3.9), "sequence")).isEqualTo(3);
    }

    @Test
    void requireInt_withParsableStringValue_returnsParsedInt() {
        assertThat(MessageFields.requireInt(Map.of("sequence", "42"), "sequence")).isEqualTo(42);
    }

    @Test
    void requireInt_withUnparsableStringValue_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireInt(Map.of("sequence", "abc"), "sequence"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid int");
    }

    @Test
    void requireInt_withNullMap_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireInt(null, "sequence"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requireInt_withMissingKey_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> MessageFields.requireInt(Map.of(), "sequence"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
