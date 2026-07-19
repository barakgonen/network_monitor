package com.example.monitor.api.publisher;

import java.util.List;

public record PublisherInterfaceDto(
        String name,
        boolean enabled,
        String protocol,
        int port,
        boolean broadcast,
        List<String> broadcastTargets,
        String byteOrder,
        String headerType,
        String opcodeFieldName,
        List<PublisherMessageDto> messages
) {
}
