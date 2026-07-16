package com.example.monitor.publishing;

import com.example.monitor.api.PeriodicPublishRequest;
import com.example.monitor.api.PeriodicPublishStatus;
import com.example.monitor.api.PublishRequest;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PeriodicPublisherService {
    private final MonitorPayloadFactory payloadFactory;
    private final UdpMessagePublisher udpMessagePublisher;
    private final TcpMessagePublisher tcpMessagePublisher;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Object lock = new Object();
    private ScheduledFuture<?> currentTask;
    private PeriodicPublishRequest currentRequest;
    private final AtomicLong sentCount = new AtomicLong(0);
    private volatile String lastError;

    public PeriodicPublisherService(
            MonitorPayloadFactory payloadFactory,
            UdpMessagePublisher udpMessagePublisher,
            TcpMessagePublisher tcpMessagePublisher
    ) {
        this.payloadFactory = payloadFactory;
        this.udpMessagePublisher = udpMessagePublisher;
        this.tcpMessagePublisher = tcpMessagePublisher;
    }

    public PeriodicPublishStatus start(PeriodicPublishRequest request) {
        validate(request);

        synchronized (lock) {
            stopInternal();

            long intervalMillis = calculateIntervalMillis(request.eventsPerTimeUnit(), request.timeUnit());
            sentCount.set(0);
            lastError = null;
            currentRequest = request;

            currentTask = scheduler.scheduleAtFixedRate(
                    () -> publishOnce(request),
                    0,
                    intervalMillis,
                    TimeUnit.MILLISECONDS
            );

            return status();
        }
    }

    public PeriodicPublishStatus stop() {
        synchronized (lock) {
            stopInternal();
            return status();
        }
    }

    public PeriodicPublishStatus status() {
        synchronized (lock) {
            boolean running = currentTask != null && !currentTask.isCancelled();

            if (currentRequest == null) {
                return new PeriodicPublishStatus(
                        running,
                        null,
                        null,
                        null,
                        0,
                        0,
                        null,
                        0,
                        sentCount.get(),
                        lastError
                );
            }

            PublishRequest publishRequest = currentRequest.publishRequest();

            return new PeriodicPublishStatus(
                    running,
                    publishRequest.interfaceName(),
                    publishRequest.messageType(),
                    publishRequest.host(),
                    publishRequest.port(),
                    currentRequest.eventsPerTimeUnit(),
                    normalizeTimeUnit(currentRequest.timeUnit()),
                    calculateIntervalMillis(currentRequest.eventsPerTimeUnit(), currentRequest.timeUnit()),
                    sentCount.get(),
                    lastError
            );
        }
    }

    private void publishOnce(PeriodicPublishRequest request) {
        try {
            PublishRequest publishRequest = request.publishRequest();

            byte[] payload = payloadFactory.create(
                    publishRequest.interfaceName(),
                    publishRequest.messageType(),
                    publishRequest.fields()
            );

            String transport = TransportSelector.normalize(publishRequest.transport());

            if ("TCP".equals(transport)) {
                tcpMessagePublisher.send(publishRequest.host(), publishRequest.port(), payload);
            } else {
                udpMessagePublisher.send(publishRequest.host(), publishRequest.port(), payload);
            }

            sentCount.incrementAndGet();
        } catch (Exception e) {
            lastError = e.getMessage();
        }
    }

    private void stopInternal() {
        if (currentTask != null) {
            currentTask.cancel(false);
            currentTask = null;
        }
    }

    private void validate(PeriodicPublishRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.publishRequest() == null) {
            throw new IllegalArgumentException("publishRequest is required");
        }

        if (request.eventsPerTimeUnit() <= 0) {
            throw new IllegalArgumentException("eventsPerTimeUnit must be greater than 0");
        }

        calculateIntervalMillis(request.eventsPerTimeUnit(), request.timeUnit());
    }

    private long calculateIntervalMillis(int eventsPerTimeUnit, String timeUnit) {
        String normalized = normalizeTimeUnit(timeUnit);

        long unitMillis = switch (normalized) {
            case "SECOND" -> 1_000L;
            case "MINUTE" -> 60_000L;
            case "HOUR" -> 3_600_000L;
            default -> throw new IllegalArgumentException("Unsupported timeUnit: " + timeUnit);
        };

        long intervalMillis = unitMillis / eventsPerTimeUnit;

        if (intervalMillis <= 0) {
            throw new IllegalArgumentException("eventsPerTimeUnit is too high for timeUnit=" + normalized);
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
