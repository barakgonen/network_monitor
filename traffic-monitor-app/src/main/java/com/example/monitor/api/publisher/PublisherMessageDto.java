package com.example.monitor.api.publisher;

import java.util.List;

public record PublisherMessageDto(
        String opcode,
        String displayName,
        String messageClass,
        List<PublisherFieldDto> fields
) {
}
