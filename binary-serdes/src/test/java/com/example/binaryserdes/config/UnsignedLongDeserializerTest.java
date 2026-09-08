package com.example.binaryserdes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UnsignedLongDeserializerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long deserialize(String opcodeLiteral) throws Exception {
        String json = "{\"name\": \"Test\", \"opcode\": " + opcodeLiteral + ", \"fields\": []}";
        return MAPPER.readValue(json, MessageConfig.class).opcode;
    }

    @Test
    void plainJsonNumber_parsesAsLong() throws Exception {
        assertThat(deserialize("1001")).isEqualTo(1001L);
    }

    @Test
    void quotedSignedRangeString_parsesAsLong() throws Exception {
        assertThat(deserialize("\"1001\"")).isEqualTo(1001L);
    }

    @Test
    void quotedUnsignedUpperHalfString_parsesViaParseUnsignedLong() throws Exception {
        assertThat(deserialize("\"18446744073709551615\"")).isEqualTo(-1L);
        assertThat(deserialize("\"9223372036854775808\"")).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void garbageString_throws() {
        assertThatThrownBy(() -> deserialize("\"not-a-number\""))
                .isInstanceOf(Exception.class);
    }
}
