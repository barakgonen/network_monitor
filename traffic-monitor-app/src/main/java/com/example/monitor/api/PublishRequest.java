package com.example.monitor.api;

import java.util.Map;

public record PublishRequest(
        String interfaceName,
        String messageType,
        String host,
        int port,
        Map<String, Object> fields
) {
}
