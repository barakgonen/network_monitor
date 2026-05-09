package com.example.messagereader.api;

public record PublishTarget(
        TransportProtocol protocol,
        String host,
        int port
) {
    public PublishTarget {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol is required");
        }

        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is required");
        }

        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range 1..65535");
        }
    }
}
