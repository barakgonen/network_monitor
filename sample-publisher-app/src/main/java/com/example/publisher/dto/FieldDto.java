package com.example.publisher.dto;

import java.util.List;

/**
 * Describes one field of a message/operation for the publisher UI's dynamic form-rendering JS
 * (copied from {@code com.example.monitor.publisher.PublisherFieldDto}'s exact shape, not
 * imported, so this app has no dependency on that about-to-be-deleted package). {@code
 * itemFields}/{@code maxLength} are only populated for array-of-struct fields - they describe one
 * element's shape so the UI can render an "add row" control instead of a single opaque field.
 */
public record FieldDto(String name, String type, List<FieldDto> itemFields, Integer maxLength) {

    public FieldDto(String name, String type) {
        this(name, type, null, null);
    }
}
