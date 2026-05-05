package com.example.schemas.weather;

public record WeatherProtocolHeader(
        int opcode,
        long sendTimeEpochMillis,
        int bodyLength
) {
}
