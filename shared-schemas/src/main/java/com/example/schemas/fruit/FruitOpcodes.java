package com.example.schemas.fruit;

public final class FruitOpcodes {
    public static final int ORANGE = 1001;

    private FruitOpcodes() {
    }

    public static String messageType(int opcode) {
        return switch (opcode) {
            case ORANGE -> "Orange";
            default -> "Unknown";
        };
    }
}
