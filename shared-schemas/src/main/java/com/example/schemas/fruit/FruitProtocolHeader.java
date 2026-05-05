package com.example.schemas.fruit;

public record FruitProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
}
