package com.example.schemas.binaryserdes;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemas.greeting.BeaconMessage;
import com.example.schemas.greeting.GreetingMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves serdes/greeting.protocol.json (repo root) matches the real wire format used by the
 * greeting interface (config/traffic-tool.yml) today - a legacy-envelope-style body (opcode
 * carried separately by ProtocolHeaderCodec, not baked into fields:), confirmed against
 * GreetingReplyEncoder (traffic-destination-app), not the leading-opcode convention the deleted
 * greeting-demo BinarySerdes interface used (that interface had no envelope at all).
 * <p>
 * Greeting's {@code text} field is variable-length, so its tests use {@code encodeInto} with a
 * manually pre-sized buffer - see {@link FruitProtocolRoundTripTest}'s class Javadoc for why.
 * Beacon has no variable-length field, so its tests use the higher-level {@code encode(name, json)}.
 */
class GreetingProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/greeting.protocol.json")) {
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
    void beacon_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        byte[] bytes = protocolOut.encode("Beacon", """
                { "lat": 32.0853, "lon": 34.7818 }
                """);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Beacon", bytes));

        assertThat(decoded.get("lat").asDouble()).isEqualTo(32.0853);
        assertThat(decoded.get("lon").asDouble()).isEqualTo(34.7818);
    }

    @Test
    void beacon_bytesMatchExistingReflectiveEncodingExactly() throws Exception {
        // Beacon's whole body is two double64 fields, so the encodings should be identical.
        BeaconMessage existing = new BeaconMessage(32.0853, 34.7818);
        ByteBuffer buf = ByteBuffer.allocate(16);
        existing.toByteArray(buf);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        byte[] newBytes = protocolOut.encode("Beacon", """
                { "lat": 32.0853, "lon": 34.7818 }
                """);

        assertThat(newBytes).isEqualTo(buf.array());
    }

    @Test
    void greeting_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        String text = "Hello from (32.0853, 34.7818)";
        String json = """
                { "id": 1, "text": "%s" }
                """.formatted(text);

        byte[] bytes = encodeInto(protocolOut, "Greeting", json, 4 + stringFieldSize(text));
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Greeting", bytes));

        assertThat(decoded.get("id").asInt()).isEqualTo(1);
        assertThat(decoded.get("text").asText()).isEqualTo("Hello from (32.0853, 34.7818)");
    }

    @Test
    void greeting_idBytesMatchExistingReflectiveEncoding() throws Exception {
        GreetingMessage existing = new GreetingMessage(1, "hi");
        byte[] existingBytes = existing.toByteArray();
        byte[] existingIdBytes = Arrays.copyOfRange(existingBytes, 0, 4);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        byte[] newBytes = encodeInto(protocolOut, "Greeting", """
                { "id": 1, "text": "hi" }
                """, 4 + stringFieldSize("hi"));
        byte[] newIdBytes = Arrays.copyOfRange(newBytes, 0, 4);

        assertThat(newIdBytes).isEqualTo(existingIdBytes);
    }
}
