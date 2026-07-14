package com.example.schemas.ping;

public final class PingOpcodes {
    public static final int PING = 3001;
    public static final int PONG = 3002;

    private PingOpcodes() {
    }

    public static String messageType(int opcode) {
        if (opcode == PING) {
            return "Ping";
        }

        if (opcode == PONG) {
            return "Pong";
        }

        return "Unknown";
    }
}
