package com.example.binaryserdes;

import com.example.binaryserdes.config.ProtocolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@code {"kind": "array", "elementType": "byte", "length": 5}} - a fixed-size byte array
 * field - round-trips end to end, driven purely from a JSON config, the same way
 * {@link ProtocolEnumConfigTest} proves out {@code kind: "enum"}.
 */
class ProtocolByteArrayConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig(String json) throws Exception {
        return Protocol.loadConfig(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void byteArrayField_roundTripsThroughJsonAsNumberArray() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "Payload", "kind": "array", "elementType": "byte", "length": 5 }
                  ],
                  "messages": [
                    {
                      "name": "Frame",
                      "opcode": 1,
                      "fields": [
                        { "name": "payload", "type": "Payload" }
                      ]
                    }
                  ]
                }
                """;

        ProtocolConfig cfg = loadConfig(configJson);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        byte[] bytes = protocolOut.encode("Frame", "{\"payload\": [1, -2, 3, -4, 127]}");
        assertThat(bytes).hasSize(5); // 5 raw bytes, one per array element

        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Frame", bytes));
        JsonNode payload = decoded.get("payload");

        assertThat(payload.isArray()).isTrue();
        assertThat(payload.size()).isEqualTo(5);
        int[] expected = {1, -2, 3, -4, 127};
        for (int i = 0; i < expected.length; i++) {
            assertThat(payload.get(i).asInt()).isEqualTo(expected[i]);
        }
    }

    @Test
    void byteArrayType_isRegisteredAsArrayTypeOfByte() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "Payload", "kind": "array", "elementType": "byte", "length": 5 }
                  ],
                  "messages": []
                }
                """;

        Type<?> type = Protocol.resolveNamedType(loadConfig(configJson), "Payload");

        assertThat(type).isInstanceOf(ArrayType.class);
        ArrayType arrayType = (ArrayType) type;
        assertThat(arrayType.getLength()).isEqualTo(5);
        assertThat(arrayType.getSizeInBytes()).isEqualTo(5);
        assertThat(arrayType.getElementType().getJavaType()).isEqualTo(Byte.class);
    }
}
