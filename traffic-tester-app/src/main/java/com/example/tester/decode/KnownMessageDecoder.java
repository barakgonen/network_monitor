package com.example.tester.decode;

import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import com.example.schemacore.reflect.ReflectiveFieldExtractor;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemas.candy.CandyMessage;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.OrangeMessage;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;
import com.example.schemas.weather.TemperatureReadingMessage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * Best-effort legacy-envelope decode of a known message type, shared by {@code UdpListener} and
 * {@code TcpListener} - both print arriving bytes the same way regardless of transport.
 */
public final class KnownMessageDecoder {

    private static final Map<Integer, Class<?>> KNOWN_MESSAGE_CLASSES_BY_OPCODE = Map.of(
            1001, OrangeMessage.class,
            1002, BananaMessage.class,
            2001, TemperatureReadingMessage.class,
            3001, PingMessage.class,
            3002, PongMessage.class,
            4001, CandyMessage.class);

    private KnownMessageDecoder() {
    }

    public static void tryDecodeAndPrint(byte[] payload) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

            Class<?> messageClass = KNOWN_MESSAGE_CLASSES_BY_OPCODE.get(header.opcode());
            if (messageClass == null) {
                return;
            }

            byte[] body = new byte[buffer.remaining()];
            buffer.get(body);

            // The legacy envelope this decodes against (see ProtocolHeaderCodec) is always
            // big-endian - unlike the monitor side, this tester tool has no per-interface config
            // to resolve a byte order from, so the expectation is spelled out explicitly.
            Object message = ReflectiveStructCodec.decode(messageClass, body, ByteOrder.BIG_ENDIAN);
            Map<String, Object> fields = ReflectiveFieldExtractor.extractFields(message);

            System.out.println("Decoded as " + messageClass.getSimpleName() + ":");
            System.out.println("  header: opcode=" + header.opcode()
                    + ", sendTimeEpochMillis=" + header.sendTimeEpochMillis()
                    + ", bodyLength=" + header.bodyLength());
            System.out.println("  body: " + fields);
        } catch (Exception ignored) {
            // Not a known message, or invalid payload.
        }
    }
}
