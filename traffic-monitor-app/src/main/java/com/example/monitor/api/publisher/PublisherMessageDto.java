package com.example.monitor.api.publisher;

public record PublisherMessageDto(
        String opcode,
        String displayName,
        String messageClass
) {
}
