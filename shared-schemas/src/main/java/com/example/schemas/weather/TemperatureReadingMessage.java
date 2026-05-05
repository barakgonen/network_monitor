package com.example.schemas.weather;

public record TemperatureReadingMessage(
        String stationId,
        double temperatureCelsius,
        WeatherCondition condition
) {
}
