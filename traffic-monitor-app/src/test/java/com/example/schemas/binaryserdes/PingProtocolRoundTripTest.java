package com.example.schemas.binaryserdes;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;

class PingProtocolRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/ping.protocol.json")) {
            return Protocol.loadConfig(in);
        }
    }

    @Test
    void ping_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        byte[] bytes = protocolOut.encode("Ping", """
                { "sequence": 1 }
                """);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Ping", bytes));

        assertThat(decoded.get("sequence").asInt()).isEqualTo(1);
    }

    @Test
    void ping_bytesMatchExistingReflectiveEncodingExactly() throws Exception {
        // Ping's entire body is a single int32 field with no variable-length parts, so the two
        // encodings should be byte-for-byte identical.
        PingMessage existing = new PingMessage(7);
        ByteBuffer buf = ByteBuffer.allocate(4);
        existing.toByteArray(buf);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        byte[] newBytes = protocolOut.encode("Ping", """
                { "sequence": 7 }
                """);

        assertThat(newBytes).isEqualTo(buf.array());
    }

    @Test
    void pong_roundTripsThroughBinarySerdes() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        byte[] bytes = protocolOut.encode("Pong", """
                { "sequence": 2 }
                """);
        JsonNode decoded = MAPPER.readTree(protocolIn.parse("Pong", bytes));

        assertThat(decoded.get("sequence").asInt()).isEqualTo(2);
    }

    @Test
    void pong_bytesMatchExistingReflectiveEncodingExactly() throws Exception {
        PongMessage existing = new PongMessage(9);
        ByteBuffer buf = ByteBuffer.allocate(4);
        existing.toByteArray(buf);

        ProtocolConfig cfg = loadConfig();
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);
        byte[] newBytes = protocolOut.encode("Pong", """
                { "sequence": 9 }
                """);

        assertThat(newBytes).isEqualTo(buf.array());
    }
}
