package com.example.monitor.model;

import java.time.Instant;

public record ObservedMessage(
        String id,
        Instant timestamp,
        String transportProtocol,
        String remoteAddress,
        int localPort,
        int payloadSizeBytes,
        String payloadText,
        String payloadBase64,
        String error
) {
}
