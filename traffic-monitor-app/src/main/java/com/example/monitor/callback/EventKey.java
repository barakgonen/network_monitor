package com.example.monitor.callback;

public record EventKey(String value) {

    public EventKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("event key cannot be null or blank");
        }
    }
}
