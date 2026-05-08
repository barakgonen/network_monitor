package com.example.schemas.weather;

import java.nio.ByteBuffer;

public record WeatherProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
    public WeatherProtocolHeader(ByteBuffer buffer) {
        this(
                buffer.getInt(),
                buffer.getLong(),
                buffer.getInt()
        );
    }

    public static WeatherProtocolHeader parse(ByteBuffer buffer) {
        return new WeatherProtocolHeader(buffer);
    }
}
