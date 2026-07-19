package com.example.schemas.weather;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record TemperatureReadingMessage(
        String stationId,
        double temperatureCelsius,
        WeatherCondition condition
) implements ProtocolMessage {

    public static TemperatureReadingMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES + Byte.BYTES) {
            throw new IllegalArgumentException("TemperatureReading body is too short");
        }

        int stationIdLength = buffer.getInt();

        if (stationIdLength < 0 || stationIdLength > buffer.remaining() - Double.BYTES - Byte.BYTES) {
            throw new IllegalArgumentException("Invalid stationId length: " + stationIdLength);
        }

        byte[] stationIdBytes = new byte[stationIdLength];
        buffer.get(stationIdBytes);

        double temperatureCelsius = buffer.getDouble();
        WeatherCondition condition = WeatherCondition.fromCode(buffer.get());

        return new TemperatureReadingMessage(
                new String(stationIdBytes, StandardCharsets.UTF_8), temperatureCelsius, condition);
    }

    public byte[] toByteArray() {
        byte[] stationIdBytes = stationId.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + stationIdBytes.length + Double.BYTES + Byte.BYTES);
        buffer.putInt(stationIdBytes.length);
        buffer.put(stationIdBytes);
        buffer.putDouble(temperatureCelsius);
        buffer.put(condition.getCode());

        return buffer.array();
    }
}
