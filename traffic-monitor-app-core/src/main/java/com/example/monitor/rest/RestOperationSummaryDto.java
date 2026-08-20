package com.example.monitor.rest;

/** REST analogue of {@link com.example.monitor.publisher.PublisherMessageDto} for listing operations in the UI. */
public record RestOperationSummaryDto(String operationId, String httpMethod, String pathTemplate, String summary) {
}
