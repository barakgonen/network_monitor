package com.example.monitor.publisher;

import java.util.Map;

public record PublisherSendRequest(
        String interfaceKey,
        String messageType,
        String host,
        Integer port,
        String transport,
        Map<String, Object> fields
) {
}
