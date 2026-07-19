package com.example.monitor.api.publisher;

import java.util.List;

public record PublisherSendResponse(
        boolean success,
        String interfaceName,
        String opcode,
        String messageClass,
        String host,
        int port,
        List<String> targets,
        int bytesSent,
        String error
) {
}
