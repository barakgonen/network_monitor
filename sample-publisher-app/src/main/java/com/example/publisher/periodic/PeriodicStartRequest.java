package com.example.publisher.periodic;

import com.example.publisher.send.SendRequest;

public record PeriodicStartRequest(SendRequest sendRequest, int eventsPerTimeUnit, String timeUnit) {
}
