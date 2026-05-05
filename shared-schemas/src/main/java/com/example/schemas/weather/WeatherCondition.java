package com.example.schemas.weather;

public enum WeatherCondition {
    SUNNY((byte) 1, "sunny"),
    CLOUDY((byte) 2, "cloudy"),
    RAINY((byte) 3, "rainy"),
    UNKNOWN((byte) 4, "unknown");

    private final byte code;
    private final String wireName;

    WeatherCondition(byte code, String wireName) {
        this.code = code;
        this.wireName = wireName;
    }

    public byte getCode() {
        return code;
    }

    public String getWireName() {
        return wireName;
    }

    public static WeatherCondition fromCode(byte code) {
        for (WeatherCondition value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static WeatherCondition fromWireName(String name) {
        if (name == null) {
            return UNKNOWN;
        }

        for (WeatherCondition value : values()) {
            if (value.wireName.equalsIgnoreCase(name)) {
                return value;
            }
        }

        return UNKNOWN;
    }
}
