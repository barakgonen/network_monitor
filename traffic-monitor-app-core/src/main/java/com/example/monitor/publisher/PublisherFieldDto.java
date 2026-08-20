package com.example.monitor.publisher;

import java.util.List;

/**
 * {@code itemFields}/{@code maxLength} are only populated for array-of-struct fields (e.g.
 * {@code trackData}) - they describe one element's shape so the generic publisher UI can render
 * an "add row" control instead of a single opaque text field. Absent (null) for every other
 * field, including scalar arrays (e.g. {@code byte[]}), which remain a single leaf field.
 */
public record PublisherFieldDto(String name, String type, List<PublisherFieldDto> itemFields, Integer maxLength) {

    public PublisherFieldDto(String name, String type) {
        this(name, type, null, null);
    }
}
