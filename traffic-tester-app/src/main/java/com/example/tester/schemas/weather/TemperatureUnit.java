package com.example.tester.schemas.weather;

/**
 * Mirrors serdes/weather.protocol.json's {@code TemperatureUnit} enum type ({@code uint8}-backed,
 * {@code CELSIUS: 0, FAHRENHEIT: 1}) - unlike {@link WeatherCondition} (a plain wire {@code
 * string}), this one is a genuine binary-serdes {@code kind: "enum"} field, so the wire
 * representation is the numeric code, not a string.
 */
public enum TemperatureUnit {
    CELSIUS((byte) 0),
    FAHRENHEIT((byte) 1);

    private final byte code;

    TemperatureUnit(byte code) {
        this.code = code;
    }

    public byte getCode() {
        return code;
    }

    public static TemperatureUnit fromCode(byte code) {
        for (TemperatureUnit value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown TemperatureUnit code: " + code);
    }

    public static TemperatureUnit fromWireName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("TemperatureUnit name must not be null");
        }
        return TemperatureUnit.valueOf(name.trim().toUpperCase());
    }
}
