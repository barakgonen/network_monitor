package com.example.schemas.weather;

public final class WeatherOpcodes {
    public static final int TEMPERATURE_READING = 2001;

    private WeatherOpcodes() {
    }

    public static String messageType(int opcode) {
        return switch (opcode) {
            case TEMPERATURE_READING -> "TemperatureReading";
            default -> "Unknown";
        };
    }
}
