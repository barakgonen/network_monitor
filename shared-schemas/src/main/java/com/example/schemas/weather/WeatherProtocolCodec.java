package com.example.schemas.weather;

import com.example.schemacore.ProtocolHeader;
import com.example.schemacore.ProtocolHeaderCodec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class WeatherProtocolCodec {

    public EncodedWeatherMessage encodeTemperatureReading(
            TemperatureReadingMessage message,
            long sendTimeEpochMillis
    ) {
        byte[] body = encodeTemperatureReadingBody(message);
        return new EncodedWeatherMessage(
                ProtocolHeaderCodec.encodeMessage(WeatherOpcodes.TEMPERATURE_READING, sendTimeEpochMillis, body));
    }

    public DecodedWeatherMessage decode(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(buffer);

        String messageType = WeatherOpcodes.messageType(header.opcode());
        Map<String, Object> bodyFields = new LinkedHashMap<>();

        if (header.opcode() == WeatherOpcodes.TEMPERATURE_READING) {
            decodeTemperatureReadingBody(buffer, bodyFields);
        }

        WeatherProtocolHeader weatherHeader = new WeatherProtocolHeader(header.opcode(), header.sendTimeEpochMillis(), header.bodyLength());
        return new DecodedWeatherMessage("Weather Interface", messageType, weatherHeader, bodyFields);
    }

    static byte[] encodeTemperatureReadingBody(TemperatureReadingMessage message) {
        byte[] stationIdBytes = message.stationId().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + stationIdBytes.length + Double.BYTES + Byte.BYTES);
        buffer.putInt(stationIdBytes.length);
        buffer.put(stationIdBytes);
        buffer.putDouble(message.temperatureCelsius());
        buffer.put(message.condition().getCode());

        return buffer.array();
    }

    static void decodeTemperatureReadingBody(ByteBuffer buffer, Map<String, Object> bodyFields) {
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
