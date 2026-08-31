package com.example.monitor.api;

import java.util.List;

public record InterfaceCatalogEntryDto(String key, String name, List<InterfaceCatalogMessageDto> messages) {
}
