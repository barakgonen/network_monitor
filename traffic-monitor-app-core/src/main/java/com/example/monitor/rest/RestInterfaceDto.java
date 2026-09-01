package com.example.monitor.rest;

import java.util.List;

/** Lists a REST interface's discovered operations for the UI (e.g. REST Auto-Reply's dropdowns). */
public record RestInterfaceDto(String key, String name, List<RestOperationSummaryDto> operations) {
}
