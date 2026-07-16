package com.example.monitor.persistence;

import com.example.monitor.model.ObservedMessage;

import java.util.List;

public record HistoryPage(List<ObservedMessage> items, long totalCount) {
}
