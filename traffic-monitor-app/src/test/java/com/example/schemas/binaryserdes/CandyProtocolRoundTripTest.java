package com.example.schemas.binaryserdes;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemas.candy.CandyMessage;
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
class CandyProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/candy.protocol.json")) {
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
    void candy_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        String json = """
                { "name": "chocolate-bar", "calories": 250.5 }
                """;

        byte[] bytes = encodeInto(protocolOut, "Candy", json, stringFieldSize("chocolate-bar") + 8);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Candy", bytes));

        assertThat(decoded.get("name").asText()).isEqualTo("chocolate-bar");
        assertThat(decoded.get("calories").asDouble()).isEqualTo(250.5);
    }

    @Test
    void candy_caloriesBytesMatchExistingReflectiveEncoding() throws Exception {
        CandyMessage existing = new CandyMessage("chocolate-bar", 250.5);
        byte[] existingBytes = existing.toByteArray();
        byte[] existingCaloriesBytes = Arrays.copyOfRange(existingBytes, existingBytes.length - 8, existingBytes.length);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        String json = """
                { "name": "chocolate-bar", "calories": 250.5 }
                """;
        byte[] newBytes = encodeInto(protocolOut, "Candy", json, stringFieldSize("chocolate-bar") + 8);
        byte[] newCaloriesBytes = Arrays.copyOfRange(newBytes, newBytes.length - 8, newBytes.length);

        assertThat(newCaloriesBytes).isEqualTo(existingCaloriesBytes);
    }
}
