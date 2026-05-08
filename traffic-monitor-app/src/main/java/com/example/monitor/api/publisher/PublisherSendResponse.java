package com.example.monitor.api.publisher;

public record PublisherSendResponse(
        boolean success,
        String interfaceName,
        String opcode,
        String messageClass,
        String host,
        int port,
        int bytesSent,
        String error
) {
}
