package com.example.schemas.reflectiondemo;

import com.example.schemas.weather.WeatherProtocolCodec;

public class ReflectionTemperatureReadingMessage {
    private final String stationId;
    private final double temperatureCelsius;
    private final String condition;

    public ReflectionTemperatureReadingMessage(byte[] payload) {
        WeatherProtocolCodec.DecodedWeatherMessage decoded = new WeatherProtocolCodec().decode(payload);

        if (!"TemperatureReading".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not TemperatureReading. actual=" + decoded.messageType());
        }

        this.stationId = String.valueOf(decoded.bodyFields().get("stationId"));
        this.temperatureCelsius = ((Number) decoded.bodyFields().get("temperatureCelsius")).doubleValue();
        this.condition = String.valueOf(decoded.bodyFields().get("condition"));
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
