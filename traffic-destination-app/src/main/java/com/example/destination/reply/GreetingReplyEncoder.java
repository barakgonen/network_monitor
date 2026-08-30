package com.example.destination.reply;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

/**
 * Decodes an incoming legacy-envelope Beacon message and builds a matching Greeting reply,
 * wire-format compatible with traffic-monitor-app's greeting interface (opcode 5001/5002,
 * always-BIG_ENDIAN legacy envelope) - the {@code GREETING} analogue of {@link PongReplyEncoder}.
 *
 * <p>Uses the same {@code com.example.schemacore.binaryserdes} JSON-schema-driven codec
 * traffic-monitor-app's {@code greeting} interface decodes against, rather than a hand-written
 * {@code BeaconMessage}/{@code GreetingMessage} pair. The schema is bundled as a classpath
 * resource (a copy of the repo-root {@code serdes/greeting.protocol.json}) rather than loaded
 * from a CWD-relative filesystem path - unlike {@code config/traffic-tool.yml}'s own
 * {@code serdesFile:}, this class has no guarantee it's ever run with the repo root as the
 * working directory (notably, unit tests run with the module directory as CWD).
 */
public final class GreetingReplyEncoder {

    private static final String SERDES_RESOURCE = "/serdes/greeting.protocol.json";
    private static final int GREETING_OPCODE = 5002;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final ProtocolIn PROTOCOL_IN;
    private static final ProtocolOut PROTOCOL_OUT;

    static {
        ProtocolConfig config = loadConfig();
        PROTOCOL_IN = ProtocolIn.fromProtocolConfig(config);
        PROTOCOL_OUT = ProtocolOut.fromProtocolConfig(config);
    }

    private GreetingReplyEncoder() {
    }

    public static byte[] buildGreetingReply(byte[] receivedBytes) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(receivedBytes);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
            byte[] body = new byte[header.bodyLength()];
            buffer.get(body);

            JsonNode beacon = MAPPER.readTree(PROTOCOL_IN.parse("Beacon", body));
            double lat = beacon.get("lat").asDouble();
            double lon = beacon.get("lon").asDouble();

            String greetingJson = MAPPER.writeValueAsString(Map.of(
                    "id", 1,
                    "text", String.format(Locale.ROOT, "Hello from (%.4f, %.4f)", lat, lon)));

            // Greeting.text is variable-length, so ProtocolOut.encode(name, json)'s self-sizing
            // buffer allocation can't be used (see SerdesMessageDefinition.encodeJson for the same
            // over-allocate-then-trim approach against ProtocolOut#encodeInto).
            ByteBuffer greetingBuffer = ByteBuffer.allocate(greetingJson.getBytes(StandardCharsets.UTF_8).length + 1024);
            PROTOCOL_OUT.encodeInto("Greeting", greetingJson, greetingBuffer);
            byte[] greetingBody = Arrays.copyOf(greetingBuffer.array(), greetingBuffer.position());

            return ProtocolHeaderCodec.encodeMessage(GREETING_OPCODE, System.currentTimeMillis(), greetingBody);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ProtocolConfig loadConfig() {
        try (InputStream in = GreetingReplyEncoder.class.getResourceAsStream(SERDES_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Classpath resource not found: " + SERDES_RESOURCE);
            }
            return Protocol.loadConfig(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load " + SERDES_RESOURCE, e);
        }
    }
}
