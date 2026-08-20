package com.example.monitor.rest;

import com.example.schemacore.reflect.FlattenedFieldPathUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Assembles a JSON-ready {@code Map}/{@code List} tree from flattened Generic Publisher form
 * fields (dotted/indexed paths, same convention {@link com.example.schemacore.reflect.ReflectiveFieldApplier}
 * uses), coerced per {@link RestSchemaNode} type/format instead of a Java {@code Class<?>} target.
 * Unlike the reflective side's fixed-length array padding (mandated by the wire format), REST
 * arrays are simply however many indices the caller submitted, compacted in ascending index order
 * - JSON arrays have no fixed-length concept, so there's nothing to pad. The output needs no
 * further Java object construction step at all - it's already directly Jackson-serializable.
 */
@Component
public class RestRequestBodyAssembler {

    public Map<String, Object> assemble(RestSchemaNode requestBodySchema, Map<String, Object> flatFields) {
        Map<String, Object> nested = FlattenedFieldPathUtil.unflatten(flatFields);
        Object assembled = coerceObject(nested, requestBodySchema);
        return assembled instanceof Map<?, ?> map ? castToStringKeyed(map) : new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castToStringKeyed(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private Map<String, Object> coerceObject(Map<?, ?> nested, RestSchemaNode schema) {
        Map<String, Object> result = new LinkedHashMap<>();

        if (schema == null || schema.properties() == null) {
            return result;
        }

        for (RestSchemaNode property : schema.properties()) {
            Object raw = nested.get(property.name());
            if (raw != null) {
                result.put(property.name(), coerceValue(raw, property));
            }
        }

        return result;
    }

    private Object coerceValue(Object raw, RestSchemaNode schema) {
        if (schema == null) {
            return raw;
        }

        return switch (schema.type()) {
            case "object" -> raw instanceof Map<?, ?> map ? coerceObject(map, schema) : raw;
            case "array" -> coerceArray(raw, schema.items());
            default -> coerceScalar(raw, schema);
        };
    }

    private List<Object> coerceArray(Object raw, RestSchemaNode itemSchema) {
        List<Object> result = new ArrayList<>();

        if (raw instanceof TreeMap<?, ?> indexedGroups) {
            for (Object itemRaw : indexedGroups.values()) {
                result.add(coerceValue(itemRaw, itemSchema));
            }
        } else if (raw instanceof List<?> list) {
            for (Object itemRaw : list) {
                result.add(coerceValue(itemRaw, itemSchema));
            }
        }

        return result;
    }

    /**
     * HTML form inputs (and generic JSON clients) send every field as a string - the same
     * gotcha {@link com.example.schemacore.reflect.ReflectiveFieldApplier#build} has to handle,
     * coerced here against an OpenAPI {@code type}/{@code format} instead of a Java target type.
     */
    private Object coerceScalar(Object raw, RestSchemaNode schema) {
        if (!(raw instanceof String stringValue) || schema == null) {
            return raw;
        }

        if (stringValue.isEmpty()) {
            return stringValue;
        }

        // NOTE: each branch must return a boxed value explicitly (not via a `cond ? long : int`
        // ternary) - Java's conditional-expression numeric promotion silently widens int32
        // results to long, autoboxing every "integer" field to Long regardless of format.
        return switch (schema.type()) {
            case "integer" -> {
                if ("int64".equals(schema.format())) {
                    yield Long.parseLong(stringValue);
                }
                yield Integer.parseInt(stringValue);
            }
            case "number" -> {
                if ("float".equals(schema.format())) {
                    yield Float.parseFloat(stringValue);
                }
                yield Double.parseDouble(stringValue);
            }
            case "boolean" -> Boolean.parseBoolean(stringValue);
            default -> stringValue;
        };
    }
}
