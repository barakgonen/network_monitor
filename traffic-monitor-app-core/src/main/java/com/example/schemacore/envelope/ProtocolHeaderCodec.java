package com.example.schemacore.envelope;

import java.nio.ByteBuffer;

/**
 * Codec for the legacy fixed 16-byte envelope (opcode:int, sendTimeEpochMillis:long,
 * bodyLength:int, big-endian) shared by the fruit/weather/ping/candy interfaces. This is the one
 * place that framing is implemented for that path; dedicated-port interfaces use their own header
 * class decoded via {@code com.example.schemacore.reflect.ReflectiveStructCodec} instead.
 */
public final class ProtocolHeaderCodec {
    public static final int HEADER_SIZE_BYTES = Integer.BYTES + Long.BYTES + Integer.BYTES;

    private ProtocolHeaderCodec() {
    }

    public static ProtocolHeader decodeHeader(ByteBuffer buffer) {
        if (buffer.remaining() < HEADER_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Payload too short for header. actual=" + buffer.remaining() + ", required=" + HEADER_SIZE_BYTES);
        }

        int opcode = buffer.getInt();
        long sendTimeEpochMillis = buffer.getLong();
        int bodyLength = buffer.getInt();

        if (bodyLength < 0) {
            throw new IllegalArgumentException("Invalid negative bodyLength: " + bodyLength);
        }

        if (buffer.remaining() != bodyLength) {
            throw new IllegalArgumentException(
                    "Invalid bodyLength. header=" + bodyLength + ", actualRemaining=" + buffer.remaining());
        }

        return new ProtocolHeader(opcode, sendTimeEpochMillis, bodyLength);
    }

    public static byte[] encodeMessage(int opcode, long sendTimeEpochMillis, byte[] body) {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + body.length);

        buffer.putInt(opcode);
        buffer.putLong(sendTimeEpochMillis);
        buffer.putInt(body.length);
        buffer.put(body);

        return buffer.array();
    }
}
