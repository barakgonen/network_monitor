package com.example.monitor.rest;

import java.util.List;

/**
 * The OpenAPI-{@code Schema}-walking analogue of a Java {@code Class<?>} field tree - built by
 * {@link RestSchemaConverter} instead of reflected, since REST message shapes have no backing
 * Java class. {@code properties} is non-null only when {@code type == "object"}; {@code items}
 * is non-null only when {@code type == "array"} (describing one element, mirroring how {@link
 * com.example.monitor.publisher.PublisherFieldDto#itemFields()} describes one array element for
 * the reflective side).
 */
public record RestSchemaNode(
        String name,
        String type,
        String format,
        List<RestSchemaNode> properties,
        RestSchemaNode items,
        Object example,
        boolean required,
        Integer maxItems
) {
}
