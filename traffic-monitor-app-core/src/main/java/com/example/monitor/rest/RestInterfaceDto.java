package com.example.monitor.rest;

import java.util.List;

/** REST analogue of {@link com.example.monitor.publisher.PublisherInterfaceDto} for listing REST interfaces in the UI. */
public record RestInterfaceDto(String key, String name, List<RestOperationSummaryDto> operations) {
}
