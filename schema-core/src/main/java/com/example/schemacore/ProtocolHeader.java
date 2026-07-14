package com.example.schemacore;

public record ProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
}
