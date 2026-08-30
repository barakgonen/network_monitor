package com.example.destination.reply;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.greeting.BeaconMessage;
import com.example.schemas.greeting.GreetingMessage;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Decodes an incoming legacy-envelope Beacon message and builds a matching Greeting reply,
 * wire-format compatible with traffic-monitor-app's greeting interface (opcode 5001/5002,
 * always-BIG_ENDIAN legacy envelope) - the {@code GREETING} analogue of {@link PongReplyEncoder}.
 */
public final class GreetingReplyEncoder {

    private static final int GREETING_OPCODE = 5002;

    private GreetingReplyEncoder() {
    }

    public static byte[] buildGreetingReply(byte[] receivedBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(receivedBytes);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        byte[] body = new byte[header.bodyLength()];
        buffer.get(body);

        BeaconMessage beacon = ReflectiveStructCodec.decode(BeaconMessage.class, body);
        GreetingMessage greeting = new GreetingMessage(
                1, String.format(Locale.ROOT, "Hello from (%.4f, %.4f)", beacon.lat(), beacon.lon()));
        byte[] greetingBody = ReflectiveStructCodec.encode(greeting);

        return ProtocolHeaderCodec.encodeMessage(GREETING_OPCODE, System.currentTimeMillis(), greetingBody);
    }
}
