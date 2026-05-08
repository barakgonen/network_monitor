package com.example.schemas.fruit;

import java.nio.ByteBuffer;

public record FruitProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
    public FruitProtocolHeader(ByteBuffer buffer) {
        this(
                buffer.getInt(),
                buffer.getLong(),
                buffer.getInt()
        );
    }

    public static FruitProtocolHeader parse(ByteBuffer buffer) {
        return new FruitProtocolHeader(buffer);
    }
}
