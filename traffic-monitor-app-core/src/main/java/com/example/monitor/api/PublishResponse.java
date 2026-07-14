package com.example.monitor.api;

public record PublishResponse(
        boolean success,
        String interfaceName,
        String messageType,
        String host,
        int port,
        int bytesSent,
        String error
) {
}
