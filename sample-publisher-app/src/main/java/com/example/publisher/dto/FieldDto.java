package com.example.publisher.dto;

import java.util.List;

/**
 * Describes one field of a message/operation for the publisher UI's dynamic form-rendering JS
 * (copied from {@code com.example.monitor.publisher.PublisherFieldDto}'s exact shape, not
 * imported, so this app has no dependency on that about-to-be-deleted package). {@code
 * itemFields}/{@code maxLength} are only populated for array-of-struct fields - they describe one
 * element's shape so the UI can render an "add row" control instead of a single opaque field.
 * {@code enumValues} is only populated for a {@code binary-serdes} {@code EnumType} field - the
 * declared symbolic names, so the UI can render a {@code <select>} instead of a free-text input.
 */
public record FieldDto(String name, String type, List<FieldDto> itemFields, Integer maxLength, List<String> enumValues) {

    public FieldDto(String name, String type) {
        this(name, type, null, null, null);
    }

    public FieldDto(String name, String type, List<FieldDto> itemFields, Integer maxLength) {
        this(name, type, itemFields, maxLength, null);
    }

    public FieldDto(String name, String type, List<String> enumValues) {
        this(name, type, null, null, enumValues);
    }
}
