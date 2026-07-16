package com.example.monitor.publishing;

import java.util.Locale;

public final class TransportSelector {
    private TransportSelector() {
    }

    public static String normalize(String transport) {
        if (transport == null || transport.isBlank()) {
            return "UDP";
        }

        String normalized = transport.trim().toUpperCase(Locale.ROOT);

        if (!normalized.equals("UDP") && !normalized.equals("TCP")) {
            throw new IllegalArgumentException("Invalid transport: " + transport);
        }

        return normalized;
    }
}
