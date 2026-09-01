package com.example.publisher.rest;

import com.example.publisher.dto.FieldDto;
import com.example.restschema.RestSchemaNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Copy of {@code com.example.monitor.rest.RestFieldMetadataService}, adapted to produce this
 * app's own {@link FieldDto} instead of {@code com.example.monitor.publisher.PublisherFieldDto} -
 * that package is deleted from traffic-monitor-app-core along with the rest of publishing, so this
 * app owns its own copy rather than depending on it. {@code RestSchemaNode} itself stays in
 * traffic-monitor-app-core (still needed by REST ingestion), so this class still reads that type
 * directly.
 */
@Component
public class RestFieldMetadataService {

    private static final int MAX_DEPTH = 6;

    public List<FieldDto> describeFields(RestSchemaNode schema) {
        List<FieldDto> out = new ArrayList<>();

        if (schema != null && "object".equals(schema.type()) && schema.properties() != null) {
            for (RestSchemaNode property : schema.properties()) {
                describeField(property, "", 0, out);
            }
        }

        return out;
    }

    private void describeField(RestSchemaNode field, String prefix, int depth, List<FieldDto> out) {
        String qualifiedName = prefix.isEmpty() ? field.name() : prefix + "." + field.name();

        if ("array".equals(field.type())) {
            String elementLabel = field.items() != null ? typeLabel(field.items()) : "string";

            if (depth < MAX_DEPTH && isObject(field.items())) {
                List<FieldDto> itemFields = new ArrayList<>();
                for (RestSchemaNode itemProperty : field.items().properties()) {
                    describeField(itemProperty, "", depth + 1, itemFields);
                }
                out.add(new FieldDto(qualifiedName, elementLabel + "[]", itemFields, field.maxItems()));
                return;
            }

            out.add(new FieldDto(qualifiedName, elementLabel + "[]"));
            return;
        }

        if ("object".equals(field.type()) && depth < MAX_DEPTH && field.properties() != null) {
            for (RestSchemaNode nestedProperty : field.properties()) {
                describeField(nestedProperty, qualifiedName, depth + 1, out);
            }
            return;
        }

        out.add(new FieldDto(qualifiedName, typeLabel(field)));
    }

    private boolean isObject(RestSchemaNode node) {
        return node != null && "object".equals(node.type()) && node.properties() != null;
    }

    private String typeLabel(RestSchemaNode node) {
        return node.format() != null ? node.type() + ":" + node.format() : node.type();
    }
}
