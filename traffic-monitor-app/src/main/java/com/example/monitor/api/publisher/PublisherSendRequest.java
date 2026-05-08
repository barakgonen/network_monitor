package com.example.monitor.api.publisher;

import java.util.Map;

public record PublisherSendRequest(
        String interfaceName,
        String opcode,
        String host,
        int port,
        Map<String, Object> fields
) {
}
