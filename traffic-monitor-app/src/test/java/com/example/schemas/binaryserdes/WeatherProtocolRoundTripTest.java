package com.example.schemas.binaryserdes;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemas.weather.TemperatureReadingMessage;
import com.example.schemas.weather.WeatherCondition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * See {@link FruitProtocolRoundTripTest}'s class Javadoc for why this uses {@code encodeInto}
 * with a manually pre-sized buffer instead of {@code encode(name, json)}.
 */
class WeatherProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/weather.protocol.json")) {
            return Protocol.loadConfig(in);
        }
    }

    private static int stringFieldSize(String value) {
        return 2 + value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static byte[] encodeInto(ProtocolOut protocolOut, String name, String json, int size) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        protocolOut.encodeInto(name, json, buffer);
        return buffer.array();
    }

    @Test
    void temperatureReading_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        String json = """
                { "stationId": "station-tlv-01", "temperatureCelsius": 28.4, "condition": "sunny" }
                """;
        int size = stringFieldSize("station-tlv-01") + 8 + stringFieldSize("sunny");

        byte[] bytes = encodeInto(protocolOut, "TemperatureReading", json, size);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("TemperatureReading", bytes));

        assertThat(decoded.get("stationId").asText()).isEqualTo("station-tlv-01");
        assertThat(decoded.get("temperatureCelsius").asDouble()).isEqualTo(28.4);
        assertThat(decoded.get("condition").asText()).isEqualTo("sunny");
    }

    @Test
    void temperatureBytesMatchExistingReflectiveEncoding() throws Exception {
        TemperatureReadingMessage existing =
                new TemperatureReadingMessage("station-tlv-01", 28.4, WeatherCondition.SUNNY);
        byte[] existingBytes = existing.toByteArray();
        // existing layout: int32 stationIdLength + stationId + double64 temperature + byte condition
        byte[] existingTemperatureBytes = Arrays.copyOfRange(
                existingBytes, existingBytes.length - 8 - 1, existingBytes.length - 1);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        String json = """
                { "stationId": "station-tlv-01", "temperatureCelsius": 28.4, "condition": "sunny" }
                """;
        int size = stringFieldSize("station-tlv-01") + 8 + stringFieldSize("sunny");
        byte[] newBytes = encodeInto(protocolOut, "TemperatureReading", json, size);
        // new layout: uint16 stationIdLength + stationId + double64 temperature + uint16 conditionLength + condition
        int conditionFieldSize = stringFieldSize("sunny");
        byte[] newTemperatureBytes = Arrays.copyOfRange(
                newBytes, newBytes.length - conditionFieldSize - 8, newBytes.length - conditionFieldSize);

        assertThat(newTemperatureBytes).isEqualTo(existingTemperatureBytes);
    }
}
