package com.example.monitor.api.publisher;

import java.util.List;

public record PublisherFieldDto(
        String name,
        String path,
        String kind,
        String javaType,
        boolean required,
        List<String> enumValues,
        List<PublisherFieldDto> children,
        Integer arrayLength
) {
}
