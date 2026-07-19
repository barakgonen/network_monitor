package com.example.schemacore.envelope;

/**
 * Decoded form of the legacy fixed 16-byte envelope: opcode, send time, and body length. Produced
 * by {@link ProtocolHeaderCodec#decodeHeader(java.nio.ByteBuffer)}.
 */
public record ProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
}
