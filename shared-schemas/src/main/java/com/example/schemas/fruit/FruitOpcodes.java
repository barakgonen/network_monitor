package com.example.schemas.fruit;

public final class FruitOpcodes {
    public static final int ORANGE = 1001;
    public static final int BANANA = 1002;

    private FruitOpcodes() {
    }

    public static String messageType(int opcode) {
        return switch (opcode) {
            case ORANGE -> "Orange";
            case BANANA -> "Banana";
            default -> "Unknown";
        };
    }
}
