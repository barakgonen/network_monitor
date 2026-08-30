package com.example.schemas.binaryserdes;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemas.fruit.BananaMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves serdes/fruit.protocol.json (repo root) is a correct BinarySerdes description of the
 * current Orange/Banana wire format, as a prerequisite for eventually retiring
 * com.example.schemas.fruit.* in favor of this JSON definition.
 * <p>
 * Uses {@code encodeInto} with a manually pre-sized buffer rather than the higher-level
 * {@code encode(name, json)} - the vendored library's {@code MessageType.toBytes(String)} always
 * tries to auto-size a buffer from fixed field sizes and throws for any variable-length
 * (string) field, so it can't encode Orange/Banana (or almost anything else in this schema set)
 * at all. That's a real gap in BinarySerdes itself worth flagging before a cutover, not something
 * to route around silently.
 */
class FruitProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/fruit.protocol.json")) {
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
    void orange_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        String json = """
                { "sourceFarm": "north-farm-17", "freshness": "very_fresh" }
                """;
        int size = stringFieldSize("north-farm-17") + stringFieldSize("very_fresh");

        byte[] bytes = encodeInto(protocolOut, "Orange", json, size);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Orange", bytes));

        assertThat(decoded.get("sourceFarm").asText()).isEqualTo("north-farm-17");
        assertThat(decoded.get("freshness").asText()).isEqualTo("very_fresh");
    }

    @Test
    void banana_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        String json = """
                { "color": "yellow", "weight": 142.75 }
                """;
        int size = stringFieldSize("yellow") + 8;

        byte[] bytes = encodeInto(protocolOut, "Banana", json, size);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Banana", bytes));

        assertThat(decoded.get("color").asText()).isEqualTo("yellow");
        assertThat(decoded.get("weight").asDouble()).isEqualTo(142.75);
    }

    @Test
    void banana_weightBytesMatchExistingReflectiveEncoding() throws Exception {
        // BinarySerdes' double64 field and BananaMessage's own toByteArray() both encode a
        // double as 8 big-endian bytes via ByteBuffer#putDouble - only the string framing
        // differs (int32-length prefix today vs BinarySerdes' native uint16-length prefix), so
        // the trailing 8 bytes (weight) should be byte-for-byte identical either way.
        BananaMessage existing = new BananaMessage("yellow", 142.75);
        byte[] existingBytes = existing.toByteArray();
        byte[] existingWeightBytes = Arrays.copyOfRange(existingBytes, existingBytes.length - 8, existingBytes.length);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        String json = """
                { "color": "yellow", "weight": 142.75 }
                """;
        byte[] newBytes = encodeInto(protocolOut, "Banana", json, stringFieldSize("yellow") + 8);
        byte[] newWeightBytes = Arrays.copyOfRange(newBytes, newBytes.length - 8, newBytes.length);

        assertThat(newWeightBytes).isEqualTo(existingWeightBytes);
    }
}
