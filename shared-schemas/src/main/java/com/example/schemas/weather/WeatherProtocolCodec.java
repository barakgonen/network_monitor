package com.example.schemas.weather;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class WeatherProtocolCodec {
    private static final int HEADER_SIZE_BYTES = Integer.BYTES + Long.BYTES + Integer.BYTES;

    public EncodedWeatherMessage encodeTemperatureReading(
            TemperatureReadingMessage message,
            long sendTimeEpochMillis
    ) {
        byte[] stationIdBytes = message.stationId().getBytes(StandardCharsets.UTF_8);

        int bodyLength = Integer.BYTES + stationIdBytes.length + Double.BYTES + Byte.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + bodyLength);

        buffer.putInt(WeatherOpcodes.TEMPERATURE_READING);
        buffer.putLong(sendTimeEpochMillis);
        buffer.putInt(bodyLength);

        buffer.putInt(stationIdBytes.length);
        buffer.put(stationIdBytes);
        buffer.putDouble(message.temperatureCelsius());
        buffer.put(message.condition().getCode());

        return new EncodedWeatherMessage(buffer.array());
    }

    public DecodedWeatherMessage decode(byte[] payload) {
        if (payload.length < HEADER_SIZE_BYTES) {
            throw new IllegalArgumentException("Payload too short for Weather header. actual=" + payload.length + ", required=" + HEADER_SIZE_BYTES);
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);

        int opcode = buffer.getInt();
        long sendTimeEpochMillis = buffer.getLong();
        int bodyLength = buffer.getInt();

        if (bodyLength < 0) {
            throw new IllegalArgumentException("Invalid negative bodyLength: " + bodyLength);
        }

        if (buffer.remaining() != bodyLength) {
            throw new IllegalArgumentException("Invalid bodyLength. header=" + bodyLength + ", actualRemaining=" + buffer.remaining());
        }

        String messageType = WeatherOpcodes.messageType(opcode);
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (opcode == WeatherOpcodes.TEMPERATURE_READING) {
            decodeTemperatureReadingBody(buffer, bodyFields);
        }

        WeatherProtocolHeader header = new WeatherProtocolHeader(opcode, sendTimeEpochMillis, bodyLength);
        return new DecodedWeatherMessage("Weather Interface", messageType, header, bodyFields);
    }

    private void decodeTemperatureReadingBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
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

        bodyFields.put("stationId", new String(stationIdBytes, StandardCharsets.UTF_8));
        bodyFields.put("temperatureCelsius", temperatureCelsius);
        bodyFields.put("condition", condition.getWireName());
    }

    public record EncodedWeatherMessage(byte[] payload) {
    }

    public record DecodedWeatherMessage(
            String interfaceName,
            String messageType,
            WeatherProtocolHeader header,
            Map<String, Object> bodyFields
    ) {
    }
}
