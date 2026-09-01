package com.example.publisher.metadata;

import java.util.List;

/** One interface's publishable messages/operations, plus its configured default port. */
public record PublishableInterfaceDto(
        String key,
        String name,
        String protocol,
        Integer defaultPort,
        List<PublishableMessageDto> messages
) {
}
