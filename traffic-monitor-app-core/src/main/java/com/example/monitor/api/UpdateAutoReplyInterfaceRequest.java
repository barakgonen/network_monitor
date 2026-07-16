package com.example.monitor.api;

public record UpdateAutoReplyInterfaceRequest(
        String interfaceName,
        boolean enabled,
        String host,
        int port,
        String transport
) {
}
