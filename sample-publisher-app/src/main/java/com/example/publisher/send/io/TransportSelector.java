package com.example.publisher.send.io;

import java.util.Locale;

/**
 * Copied from {@code com.example.monitor.publishing.TransportSelector} (traffic-monitor-app-core)
 * rather than depended on, so this app has zero dependency on traffic-monitor-app/-core - see
 * CLAUDE.md's "Publishing lives in sample-publisher-app, not here".
 */
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
