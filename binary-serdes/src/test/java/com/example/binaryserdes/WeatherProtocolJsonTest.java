package com.example.binaryserdes;

import com.example.binaryserdes.config.ProtocolConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Sanity-checks the real repo-root serdes/weather.protocol.json (not a test fixture copy),
 * loaded the same way MessageSchemaWiringConfig does - see {@link RadaProtocolJsonTest} for the
 * rada equivalent. Proves the {@code TemperatureUnit} enum type (CELSIUS/FAHRENHEIT) round-trips
 * as a symbolic JSON string rather than its raw wire byte.
 */
class WeatherProtocolJsonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadRealWeatherProtocolConfig() throws Exception {
        Path path = findRepoRootSerdesFile();
        try (InputStream in = Files.newInputStream(path)) {
            return Protocol.loadConfig(in);
        }
    }

    /** Test runs with the module directory as CWD (Maven default), so walk up to the repo root. */
    private static Path findRepoRootSerdesFile() {
        Path candidate = Paths.get("serdes/weather.protocol.json");
        if (Files.exists(candidate)) {
            return candidate;
        }
        return Paths.get("../serdes/weather.protocol.json");
    }

    @Test
    void temperatureReading_roundTripsWithFahrenheitUnit() throws Exception {
        ProtocolConfig cfg = loadRealWeatherProtocolConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        String json = """
                {
                  "stationId": "station-tlv-01",
                  "temperature": 84.2,
                  "unit": "FAHRENHEIT",
                  "condition": "sunny"
                }
                """;

        byte[] bytes = encode(protocolOut, json);

        JsonNode decoded = MAPPER.readTree(protocolIn.parse(2001, bytes));
        assertThat(decoded.get("stationId").asText()).isEqualTo("station-tlv-01");
        assertThat(decoded.get("temperature").asDouble()).isEqualTo(84.2);
        assertThat(decoded.get("unit").asText()).isEqualTo("FAHRENHEIT");
        assertThat(decoded.get("condition").asText()).isEqualTo("sunny");
    }

    @Test
    void temperatureReading_roundTripsWithCelsiusUnit() throws Exception {
        ProtocolConfig cfg = loadRealWeatherProtocolConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        byte[] bytes = encode(protocolOut,
                "{\"stationId\": \"s1\", \"temperature\": 21.5, \"unit\": \"CELSIUS\", \"condition\": \"cloudy\"}");

        JsonNode decoded = MAPPER.readTree(protocolIn.parse(2001, bytes));
        assertThat(decoded.get("unit").asText()).isEqualTo("CELSIUS");
    }

    @Test
    void temperatureReading_withUnknownUnit_throws() throws Exception {
        ProtocolConfig cfg = loadRealWeatherProtocolConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        assertThatThrownBy(() -> encode(protocolOut,
                "{\"stationId\": \"s1\", \"temperature\": 21.5, \"unit\": \"KELVIN\", \"condition\": \"cloudy\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("KELVIN");
    }

    /**
     * {@code stationId}/{@code condition} are variable-length strings, so {@link
     * ProtocolOut#encode(int, String)}'s auto-sized buffer overload throws (it can't know the
     * encoded size up front) - over-allocate and trim instead, the same trick {@code
     * SerdesMessageDefinition.encodeJson} uses for real ingestion traffic.
     */
    private static byte[] encode(ProtocolOut protocolOut, String json) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(json.length() + 256);
        protocolOut.encodeInto(2001, json, buffer);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}
