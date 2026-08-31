package com.example.publisher.periodic;

import java.time.Instant;

public record PeriodicJobDto(
        String jobId,
        String interfaceKey,
        String messageId,
        String host,
        Integer port,
        String transport,
        int eventsPerTimeUnit,
        String timeUnit,
        long intervalMillis,
        boolean running,
        long sentCount,
        String lastError,
        Instant startedAt
) {
}
