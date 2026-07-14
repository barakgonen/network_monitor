package com.example.schemas.weather;

import com.example.schemacore.ProtocolMessage;

public record TemperatureReadingMessage(
        String stationId,
        double temperatureCelsius,
        WeatherCondition condition
) implements ProtocolMessage {
}
