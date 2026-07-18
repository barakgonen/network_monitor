package com.example.schemas.candy;

public final class CandyOpcodes {
    public static final int CANDY = 4001;

    private CandyOpcodes() {
    }

    public static String messageType(int opcode) {
        if (opcode == CANDY) {
            return "Candy";
        }
        return "Unknown";
    }
}
