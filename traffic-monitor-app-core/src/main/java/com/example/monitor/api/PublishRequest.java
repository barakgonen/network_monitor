package com.example.monitor.api;

import java.util.Map;

public record PublishRequest(
        String interfaceName,
        String messageType,
        String host,
        int port,
        String transport,
        Map<String, Object> fields
) {
}
