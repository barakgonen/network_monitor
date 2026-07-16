package com.example.monitor.persistence;

import java.time.Instant;

public record HistoryQuery(
        String interfaceName,
        String messageType,
        boolean parseErrorOnly,
        Instant from,
        Instant to,
        int limit,
        int offset
) {
}
