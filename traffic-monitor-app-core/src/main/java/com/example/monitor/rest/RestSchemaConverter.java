package com.example.monitor.rest;

import io.swagger.v3.oas.models.media.Schema;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Walks a resolved OpenAPI {@link Schema} into a {@link RestSchemaNode} tree - the schema-object
 * analogue of {@link com.example.monitor.publisher.PublisherFieldMetadataService}'s
 * reflection-based recursion, same {@code MAX_DEPTH} guard (more important here, since OpenAPI
 * schemas can genuinely self-reference, e.g. a tree-shaped type, which reflection over a fixed
 * Java class tree never has to worry about).
 *
 * <p>{@code oneOf}/{@code anyOf} are collapsed to their first alternative for form-rendering
 * purposes - full polymorphic body support is out of scope (documented limitation, not an
 * oversight).
 */
@Component
public class RestSchemaConverter {

    private static final int MAX_DEPTH = 6;

    public RestSchemaNode convert(Schema<?> schema, String name) {
        return convert(schema, name, false, 0);
    }

    @SuppressWarnings("unchecked")
    private RestSchemaNode convert(Schema<?> schema, String name, boolean required, int depth) {
        Schema<?> resolved = firstAlternative(schema);
        String type = resolveType(resolved);

        if (depth >= MAX_DEPTH) {
            return new RestSchemaNode(name, type, resolved.getFormat(), null, null, resolved.getExample(), required, null);
        }

        if ("array".equals(type)) {
            RestSchemaNode items = resolved.getItems() != null
                    ? convert(resolved.getItems(), "", false, depth + 1)
                    : new RestSchemaNode("", "string", null, null, null, null, false, null);
            return new RestSchemaNode(name, type, resolved.getFormat(), null, items, resolved.getExample(), required, resolved.getMaxItems());
        }

        if ("object".equals(type) && resolved.getProperties() != null) {
            List<String> requiredNames = resolved.getRequired() != null ? resolved.getRequired() : List.of();
            List<RestSchemaNode> properties = new ArrayList<>();

            for (Map.Entry<String, Schema> entry : ((Map<String, Schema>) resolved.getProperties()).entrySet()) {
                boolean propertyRequired = requiredNames.contains(entry.getKey());
                properties.add(convert(entry.getValue(), entry.getKey(), propertyRequired, depth + 1));
            }

            return new RestSchemaNode(name, type, resolved.getFormat(), properties, null, resolved.getExample(), required, null);
        }

        return new RestSchemaNode(name, type, resolved.getFormat(), null, null, resolved.getExample(), required, null);
    }

    /**
     * {@code oneOf}/{@code anyOf} collapse to their first listed alternative; {@code allOf} is
     * expected to already be merged by {@link RestSwaggerLoader}'s {@code resolveCombinators}
     * option, but falls back to the first entry here too if it wasn't.
     */
    private Schema<?> firstAlternative(Schema<?> schema) {
        if (schema.getOneOf() != null && !schema.getOneOf().isEmpty()) {
            return schema.getOneOf().get(0);
        }
        if (schema.getAnyOf() != null && !schema.getAnyOf().isEmpty()) {
            return schema.getAnyOf().get(0);
        }
        if (schema.getAllOf() != null && !schema.getAllOf().isEmpty() && schema.getProperties() == null) {
            return schema.getAllOf().get(0);
        }
        return schema;
    }

    private String resolveType(Schema<?> schema) {
        if (schema.getType() != null) {
            return schema.getType();
        }
        if (schema.getTypes() != null && !schema.getTypes().isEmpty()) {
            return schema.getTypes().iterator().next();
        }
        if (schema.getProperties() != null) {
            return "object";
        }
        return "string";
    }
}
