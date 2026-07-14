package com.example.monitor.api;

public record PeriodicPublishRequest(
        PublishRequest publishRequest,
        int eventsPerTimeUnit,
        String timeUnit
) {
}
