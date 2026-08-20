package com.example.monitor.rest;

import com.example.monitor.publisher.PublisherFieldDto;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link RestSchemaNode}-walking analogue of
 * {@link com.example.monitor.publisher.PublisherFieldMetadataService} - same flatten-to-dotted-
 * paths / box-up-array-of-struct-fields shape, but reading a schema tree instead of reflecting a
 * {@code Class<?>}. Reuses {@link PublisherFieldDto} unchanged, so the Generic Publisher UI's
 * field-rendering JS (which only ever consumes that DTO's shape) needs no changes to also render
 * REST operation forms.
 */
@Component
public class RestFieldMetadataService {

    private static final int MAX_DEPTH = 6;

    public List<PublisherFieldDto> describeFields(RestSchemaNode schema) {
        List<PublisherFieldDto> out = new ArrayList<>();

        if (schema != null && "object".equals(schema.type()) && schema.properties() != null) {
            for (RestSchemaNode property : schema.properties()) {
                describeField(property, "", 0, out);
            }
        }

        return out;
    }

    private void describeField(RestSchemaNode field, String prefix, int depth, List<PublisherFieldDto> out) {
        String qualifiedName = prefix.isEmpty() ? field.name() : prefix + "." + field.name();

        if ("array".equals(field.type())) {
            String elementLabel = field.items() != null ? typeLabel(field.items()) : "string";

            if (depth < MAX_DEPTH && isObject(field.items())) {
                List<PublisherFieldDto> itemFields = new ArrayList<>();
                for (RestSchemaNode itemProperty : field.items().properties()) {
                    describeField(itemProperty, "", depth + 1, itemFields);
                }
                out.add(new PublisherFieldDto(qualifiedName, elementLabel + "[]", itemFields, field.maxItems()));
                return;
            }

            out.add(new PublisherFieldDto(qualifiedName, elementLabel + "[]"));
            return;
        }

        if ("object".equals(field.type()) && depth < MAX_DEPTH && field.properties() != null) {
            for (RestSchemaNode nestedProperty : field.properties()) {
                describeField(nestedProperty, qualifiedName, depth + 1, out);
            }
            return;
        }

        out.add(new PublisherFieldDto(qualifiedName, typeLabel(field)));
    }

    private boolean isObject(RestSchemaNode node) {
        return node != null && "object".equals(node.type()) && node.properties() != null;
    }

    private String typeLabel(RestSchemaNode node) {
        return node.format() != null ? node.type() + ":" + node.format() : node.type();
    }
}
