package com.example.monitor.api;

import com.example.monitor.model.ObservedMessage;

import java.util.List;

public record HistoryResponse(List<ObservedMessage> items, long totalCount, int limit, int offset) {
}
