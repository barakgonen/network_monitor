package com.example.schemas.reflectiondemo;

import com.example.schemas.weather.WeatherProtocolCodec;

import java.nio.ByteBuffer;

public class ReflectionTemperatureReadingMessage {
    private final String stationId;
    private final double temperatureCelsius;
    private final String condition;

    public ReflectionTemperatureReadingMessage(byte[] payload) {
        this(ByteBuffer.wrap(payload));
    }

    public ReflectionTemperatureReadingMessage(ByteBuffer buffer) {
        int start = buffer.position();
        byte[] remaining = new byte[buffer.remaining()];
        buffer.get(remaining);

        WeatherProtocolCodec.DecodedWeatherMessage decoded = new WeatherProtocolCodec().decode(remaining);

        if (!"TemperatureReading".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not TemperatureReading. actual=" + decoded.messageType());
        }

        this.stationId = String.valueOf(decoded.bodyFields().get("stationId"));
        this.temperatureCelsius = ((Number) decoded.bodyFields().get("temperatureCelsius")).doubleValue();
        this.condition = String.valueOf(decoded.bodyFields().get("condition"));

        buffer.position(start + remaining.length);
    }

    public String getStationId() {
        return stationId;
    }

    public double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public String getCondition() {
        return condition;
    }
}
