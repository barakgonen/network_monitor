package com.example.serdesgenerator.fixtures;

/** Enum with an explicit numeric getCode() - mirrors TemperatureUnit's shape. */
public enum CodedEnum {
    A(1),
    B(5);

    private final int code;

    CodedEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
