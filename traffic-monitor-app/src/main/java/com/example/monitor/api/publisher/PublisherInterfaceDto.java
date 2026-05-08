package com.example.monitor.api.publisher;

import java.util.List;

public record PublisherInterfaceDto(
        String name,
        String protocol,
        int port,
        String byteOrder,
        String headerType,
        String opcodeFieldName,
        List<PublisherMessageDto> messages
) {
}
