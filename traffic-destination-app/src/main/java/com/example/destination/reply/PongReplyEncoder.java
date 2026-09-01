package com.example.destination.reply;

import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.ProtocolOut;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Decodes an incoming legacy-envelope Ping message and builds a matching Pong reply, wire-format
 * compatible with traffic-monitor-app's ping interface (opcode 3001/3002, always-BIG_ENDIAN
 * legacy envelope). Uses the same {@code com.example.binaryserdes} JSON-schema-driven
 * codec traffic-monitor-app's {@code ping} interface decodes against, rather than a hand-written
 * {@code PingMessage}/{@code PongMessage} pair. The schema is bundled as a classpath resource (a
 * copy of the repo-root {@code serdes/ping.protocol.json}) rather than loaded from a CWD-relative
 * filesystem path - see {@link GreetingReplyEncoder}'s javadoc for why.
 */
public final class PongReplyEncoder {

    private static final String SERDES_RESOURCE = "/serdes/ping.protocol.json";
    private static final int PONG_OPCODE = 3002;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProtocolIn PROTOCOL_IN;
    private static final ProtocolOut PROTOCOL_OUT;

    static {
        ProtocolConfig config = loadConfig();
        PROTOCOL_IN = ProtocolIn.fromProtocolConfig(config);
        PROTOCOL_OUT = ProtocolOut.fromProtocolConfig(config);
    }

    private PongReplyEncoder() {
    }

    public static byte[] buildPongReply(byte[] receivedBytes) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(receivedBytes);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
            byte[] body = new byte[header.bodyLength()];
            buffer.get(body);

            JsonNode ping = MAPPER.readTree(PROTOCOL_IN.parse("Ping", body));
            int sequence = ping.get("sequence").asInt();

            String pongJson = MAPPER.writeValueAsString(Map.of("sequence", sequence));
            byte[] pongBody = PROTOCOL_OUT.encode("Pong", pongJson);

            return ProtocolHeaderCodec.encodeMessage(PONG_OPCODE, System.currentTimeMillis(), pongBody);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProtocolConfig loadConfig() {
        try (InputStream in = PongReplyEncoder.class.getResourceAsStream(SERDES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + SERDES_RESOURCE);
            }
            return Protocol.loadConfig(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + SERDES_RESOURCE, e);
        }
    }
}
