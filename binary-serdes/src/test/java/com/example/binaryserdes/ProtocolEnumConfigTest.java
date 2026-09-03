package com.example.binaryserdes;

import com.example.binaryserdes.config.ProtocolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves {@code kind: "enum"} is a supported {@code types:} entry end to end, driven purely from
 * a JSON config (the same path {@code serdes/weather.protocol.json}'s {@code TemperatureUnit}
 * type takes at real startup) rather than constructing an {@link EnumType} by hand.
 */
class ProtocolEnumConfigTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig(String json) throws Exception {
        return Protocol.loadConfig(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void enumField_roundTripsThroughJsonAsSymbolicName() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "TemperatureUnit", "kind": "enum", "underlyingType": "uint8",
                      "values": { "CELSIUS": 0, "FAHRENHEIT": 1 } }
                  ],
                  "messages": [
                    {
                      "name": "Reading",
                      "opcode": 1,
                      "fields": [
                        { "name": "temperature", "type": "double64" },
                        { "name": "unit", "type": "TemperatureUnit" }
                      ]
                    }
                  ]
                }
                """;

        ProtocolConfig cfg = loadConfig(configJson);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        byte[] bytes = protocolOut.encode("Reading", "{\"temperature\": 100.0, \"unit\": \"FAHRENHEIT\"}");
        assertThat(bytes).hasSize(9); // double64(8) + uint8(1)

        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Reading", bytes));
        assertThat(decoded.get("temperature").asDouble()).isEqualTo(100.0);
        assertThat(decoded.get("unit").asText()).isEqualTo("FAHRENHEIT");
    }

    @Test
    void enumType_isRegisteredAsEnumTypeInstance() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "TemperatureUnit", "kind": "enum", "underlyingType": "uint8",
                      "values": { "CELSIUS": 0, "FAHRENHEIT": 1 } }
                  ],
                  "messages": []
                }
                """;

        Type<?> type = Protocol.resolveNamedType(loadConfig(configJson), "TemperatureUnit");

        assertThat(type).isInstanceOf(EnumType.class);
        assertThat(((EnumType) type).getValuesByName()).containsEntry("CELSIUS", 0).containsEntry("FAHRENHEIT", 1);
    }

    @Test
    void enumType_withMissingValues_throws() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "TemperatureUnit", "kind": "enum", "underlyingType": "uint8" }
                  ],
                  "messages": []
                }
                """;

        ProtocolConfig cfg = loadConfig(configJson);

        assertThatThrownBy(() -> Protocol.resolveNamedType(cfg, "TemperatureUnit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("values");
    }

    @Test
    void enumType_withUnknownUnderlyingType_throws() throws Exception {
        String configJson = """
                {
                  "types": [
                    { "name": "TemperatureUnit", "kind": "enum", "underlyingType": "nope",
                      "values": { "CELSIUS": 0 } }
                  ],
                  "messages": []
                }
                """;

        ProtocolConfig cfg = loadConfig(configJson);

        assertThatThrownBy(() -> Protocol.resolveNamedType(cfg, "TemperatureUnit"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nope");
    }
}
