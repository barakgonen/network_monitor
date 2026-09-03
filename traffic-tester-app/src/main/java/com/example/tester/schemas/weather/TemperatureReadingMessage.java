package com.example.tester.schemas.weather;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record TemperatureReadingMessage(
        String stationId,
        double temperature,
        TemperatureUnit unit,
        WeatherCondition condition
) {

    public static TemperatureReadingMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES + Byte.BYTES + Integer.BYTES) {
            throw new IllegalArgumentException("TemperatureReading body is too short");
        }

        int stationIdLength = buffer.getInt();

        if (stationIdLength < 0
                || stationIdLength > buffer.remaining() - Double.BYTES - Byte.BYTES - Integer.BYTES) {
            throw new IllegalArgumentException("Invalid stationId length: " + stationIdLength);
        }

        byte[] stationIdBytes = new byte[stationIdLength];
        buffer.get(stationIdBytes);

        double temperature = buffer.getDouble();
        TemperatureUnit unit = TemperatureUnit.fromCode(buffer.get());

        int conditionLength = buffer.getInt();
        if (conditionLength < 0 || conditionLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid condition length: " + conditionLength);
        }
        byte[] conditionBytes = new byte[conditionLength];
        buffer.get(conditionBytes);
        WeatherCondition condition = WeatherCondition.fromWireName(new String(conditionBytes, StandardCharsets.UTF_8));

        return new TemperatureReadingMessage(
                new String(stationIdBytes, StandardCharsets.UTF_8), temperature, unit, condition);
    }

    public byte[] toByteArray() {
        byte[] stationIdBytes = stationId.getBytes(StandardCharsets.UTF_8);
        byte[] conditionBytes = condition.getWireName().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(
                Integer.BYTES + stationIdBytes.length + Double.BYTES + Byte.BYTES
                        + Integer.BYTES + conditionBytes.length);
        buffer.putInt(stationIdBytes.length);
        buffer.put(stationIdBytes);
        buffer.putDouble(temperature);
        buffer.put(unit.getCode());
        buffer.putInt(conditionBytes.length);
        buffer.put(conditionBytes);

        return buffer.array();
    }
}
