package com.example.monitor.publisher;

import java.util.List;

public record PublisherInterfaceDto(String key, String name, List<PublisherMessageDto> messages) {
}
