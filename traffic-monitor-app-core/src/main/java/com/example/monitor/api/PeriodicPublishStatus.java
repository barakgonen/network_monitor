package com.example.monitor.api;

public record PeriodicPublishStatus(
        boolean running,
        String interfaceName,
        String messageType,
        String host,
        int port,
        int eventsPerTimeUnit,
        String timeUnit,
        long intervalMillis,
        long sentCount,
        String lastError
) {
}
