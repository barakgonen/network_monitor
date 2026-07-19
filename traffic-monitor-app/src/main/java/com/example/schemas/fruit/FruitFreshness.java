package com.example.schemas.fruit;

public enum FruitFreshness {
    VERY_FRESH((byte) 1, "very_fresh"),
    NOT_FRESH((byte) 2, "not_fresh"),
    UNKNOWN((byte) 3, "unknown");

    private final byte code;
    private final String wireName;

    FruitFreshness(byte code, String wireName) {
        this.code = code;
        this.wireName = wireName;
    }

    public byte getCode() {
        return code;
    }

    public String getWireName() {
        return wireName;
    }

    public static FruitFreshness fromCode(byte code) {
        for (FruitFreshness value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        return UNKNOWN;
    }

    public static FruitFreshness fromWireName(String name) {
        if (name == null) {
            return UNKNOWN;
        }

        for (FruitFreshness value : values()) {
            if (value.wireName.equalsIgnoreCase(name)) {
                return value;
            }
        }

        return UNKNOWN;
    }
}
