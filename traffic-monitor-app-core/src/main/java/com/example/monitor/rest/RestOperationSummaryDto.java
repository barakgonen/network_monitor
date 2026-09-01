package com.example.monitor.rest;

/** One discovered REST operation, for listing in the UI. */
public record RestOperationSummaryDto(String operationId, String httpMethod, String pathTemplate, String summary) {
}
