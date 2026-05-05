package com.example.schemas;

import java.util.Map;

public record ParsedMessage(
        String type,
        Map<String, Object> fields
) {
}
