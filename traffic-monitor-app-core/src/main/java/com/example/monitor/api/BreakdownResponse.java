package com.example.monitor.api;

import java.util.List;

public record BreakdownResponse(String groupBy, List<BreakdownEntry> entries) {
}
