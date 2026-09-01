package com.example.publisher.periodic;

import com.example.publisher.send.SendOrchestrationService;
import com.example.publisher.send.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Generalizes {@code com.example.monitor.publishing.PeriodicPublisherService}'s single-global-slot
 * design to N independently start/stop-able concurrent jobs, working uniformly across UDP/TCP/REST
 * via {@link SendOrchestrationService} (the old service only ever handled one opcode-based UDP/TCP
 * job at a time). A shared fixed-size thread pool (not one thread per job) is enough since sends
 * are quick fire-and-forget/short HTTP calls, not long-running work.
 */
@Component
public class PeriodicSchedulerService {

    private final SendOrchestrationService sendOrchestrationService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final Map<String, PeriodicJob> jobs = new ConcurrentHashMap<>();

    public PeriodicSchedulerService(SendOrchestrationService sendOrchestrationService) {
        this.sendOrchestrationService = sendOrchestrationService;
    }

    public PeriodicJobDto start(PeriodicStartRequest request) {
        if (request.sendRequest() == null) {
            throw new IllegalArgumentException("sendRequest is required");
        }
        if (request.eventsPerTimeUnit() <= 0) {
            throw new IllegalArgumentException("eventsPerTimeUnit must be greater than 0");
        }

        String normalizedTimeUnit = normalizeTimeUnit(request.timeUnit());
        long intervalMillis = calculateIntervalMillis(request.eventsPerTimeUnit(), normalizedTimeUnit);

        String jobId = UUID.randomUUID().toString();
        PeriodicJob job = new PeriodicJob(
                jobId, request.sendRequest(), request.eventsPerTimeUnit(), normalizedTimeUnit,
                intervalMillis, Instant.now());

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(
                () -> sendOnce(job), 0, intervalMillis, TimeUnit.MILLISECONDS);
        job.future = future;

        jobs.put(jobId, job);
        return job.toDto();
    }

    public Optional<PeriodicJobDto> stop(String jobId) {
        PeriodicJob job = jobs.get(jobId);
        if (job == null) {
            return Optional.empty();
        }

        job.future.cancel(false);
        job.running = false;
        return Optional.of(job.toDto());
    }

    public List<PeriodicJobDto> list() {
        return jobs.values().stream().map(PeriodicJob::toDto).toList();
    }

    public Optional<PeriodicJobDto> status(String jobId) {
        return Optional.ofNullable(jobs.get(jobId)).map(PeriodicJob::toDto);
    }

    private void sendOnce(PeriodicJob job) {
        try {
            SendResult result = sendOrchestrationService.send(job.sendRequest);
            if (result.success()) {
                job.sentCount.incrementAndGet();
                job.lastError = null;
            } else {
                job.lastError = result.error();
            }
        } catch (Exception e) {
            job.lastError = e.getMessage();
        }
    }

    private long calculateIntervalMillis(int eventsPerTimeUnit, String normalizedTimeUnit) {
        long unitMillis = switch (normalizedTimeUnit) {
            case "SECOND" -> 1_000L;
            case "MINUTE" -> 60_000L;
            case "HOUR" -> 3_600_000L;
            default -> throw new IllegalArgumentException("Unsupported timeUnit: " + normalizedTimeUnit);
        };

        long intervalMillis = unitMillis / eventsPerTimeUnit;
        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("eventsPerTimeUnit is too high for timeUnit=" + normalizedTimeUnit);
        }
        return intervalMillis;
    }

    private String normalizeTimeUnit(String timeUnit) {
        if (timeUnit == null || timeUnit.isBlank()) {
            return "SECOND";
        }
        return timeUnit.trim().toUpperCase(Locale.ROOT);
    }
}
