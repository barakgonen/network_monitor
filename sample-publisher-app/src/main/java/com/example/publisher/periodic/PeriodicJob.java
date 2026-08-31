package com.example.publisher.periodic;

import com.example.publisher.send.SendRequest;

import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Internal mutable state for one running (or stopped-but-still-listed) periodic job. Multiple
 * jobs may target the same (interface, message) with different targets/fields/intervals - this is
 * a testing tool, so restricting to one job per message would just be an arbitrary limitation.
 */
final class PeriodicJob {
    final String jobId;
    final SendRequest sendRequest;
    final int eventsPerTimeUnit;
    final String timeUnit;
    final long intervalMillis;
    final Instant startedAt;
    final AtomicLong sentCount = new AtomicLong();

    volatile ScheduledFuture<?> future;
    volatile String lastError;
    volatile boolean running = true;

    PeriodicJob(String jobId, SendRequest sendRequest, int eventsPerTimeUnit, String timeUnit,
                long intervalMillis, Instant startedAt) {
        this.jobId = jobId;
        this.sendRequest = sendRequest;
        this.eventsPerTimeUnit = eventsPerTimeUnit;
        this.timeUnit = timeUnit;
        this.intervalMillis = intervalMillis;
        this.startedAt = startedAt;
    }

    PeriodicJobDto toDto() {
        return new PeriodicJobDto(
                jobId, sendRequest.interfaceKey(), sendRequest.messageId(),
                sendRequest.host(), sendRequest.port(), sendRequest.transport(),
                eventsPerTimeUnit, timeUnit, intervalMillis, running,
                sentCount.get(), lastError, startedAt);
    }
}
