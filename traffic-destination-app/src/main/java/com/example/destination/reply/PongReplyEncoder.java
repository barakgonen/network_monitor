package com.example.destination.reply;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;

import java.nio.ByteBuffer;

/**
 * Decodes an incoming legacy-envelope Ping message and builds a matching Pong reply, wire-format
 * compatible with traffic-monitor-app's ping interface (opcode 3001/3002, always-BIG_ENDIAN
 * legacy envelope). This is the one place traffic-destination-app needs to be protocol-aware
 * rather than just echoing raw bytes - producing a real Pong requires understanding the Ping
 * message shape, which is why this app now depends on traffic-monitor-app the same way
 * traffic-tester-app already does.
 */
public final class PongReplyEncoder {

    private static final int PONG_OPCODE = 3002;

    private PongReplyEncoder() {
    }

    public static byte[] buildPongReply(byte[] receivedBytes) {
        ByteBuffer buffer = ByteBuffer.wrap(receivedBytes);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);
        byte[] body = new byte[header.bodyLength()];
        buffer.get(body);

        PingMessage ping = ReflectiveStructCodec.decode(PingMessage.class, body);
        PongMessage pong = new PongMessage(ping.sequence());
        byte[] pongBody = ReflectiveStructCodec.encode(pong);

        return ProtocolHeaderCodec.encodeMessage(PONG_OPCODE, System.currentTimeMillis(), pongBody);
    }
}
