package com.example.monitor.model;

import java.time.Instant;
import java.util.Map;

public record ObservedMessage(
        String id,
        Instant observedAt,
        String observedAtDisplay,
        String transportProtocol,
        String remoteAddress,
        int localPort,
        String interfaceName,
        String messageType,
        Map<String, Object> header,
        Map<String, Object> body,
        int payloadSizeBytes,
        String payloadText,
        String payloadBase64,
        String parseError
) {
}
