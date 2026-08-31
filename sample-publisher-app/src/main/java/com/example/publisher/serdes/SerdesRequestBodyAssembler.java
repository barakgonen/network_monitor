package com.example.publisher.serdes;

import com.example.schemacore.binaryserdes.ArrayType;
import com.example.schemacore.binaryserdes.MessageField;
import com.example.schemacore.binaryserdes.MessageType;
import com.example.schemacore.binaryserdes.RecordType;
import com.example.schemacore.binaryserdes.Type;
import com.example.schemacore.reflect.FlattenedFieldPathUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Assembles a JSON-ready {@code Map<String,Object>} from flattened publisher form fields
 * (dotted/indexed paths, same convention {@code com.example.schemacore.reflect.ReflectiveFieldApplier}
 * and {@code com.example.monitor.rest.RestRequestBodyAssembler} use), coerced per the serdes {@link
 * Type} tree instead of a Java {@code Class<?>} or an OpenAPI schema. Unlike REST's arrays (however
 * many indices the caller submitted - JSON has no fixed-length concept), {@link ArrayType}'s wire
 * format demands exactly {@link ArrayType#getLength()} elements, so any index the caller didn't
 * submit is padded with a default value - mirrors {@code ReflectiveFieldApplier.buildArray}'s
 * "default-constructed element" padding, just keyed off {@link Type#getJavaType()} instead of a
 * Java array's component class. The output is directly {@code ObjectMapper.writeValueAsString}-able,
 * ready for {@code ProtocolOut.encode(messageName, json, byteOrder)}.
 */
@Component
public class SerdesRequestBodyAssembler {

    public Map<String, Object> assemble(MessageType messageType, Map<String, Object> flatFields) {
        Map<String, Object> nested = FlattenedFieldPathUtil.unflatten(flatFields);
        Map<String, Object> result = new LinkedHashMap<>();

        for (MessageField<?> field : messageType.getFields()) {
            Object raw = nested.get(field.getFieldName());
            Object value = raw != null ? coerceValue(raw, field.getType()) : defaultValueFor(field.getType());
            result.put(field.getFieldName(), value);
        }

        return result;
    }

    private Object coerceValue(Object raw, Type<?> type) {
        if (type instanceof RecordType recordType) {
            return raw instanceof Map<?, ?> map ? coerceObject(map, recordType) : defaultValueFor(recordType);
        }

        if (type instanceof ArrayType arrayType) {
            return coerceArray(raw, arrayType);
        }

        return coerceScalar(raw, type);
    }

    private Map<String, Object> coerceObject(Map<?, ?> nested, RecordType recordType) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Type<?>> fieldEntry : recordType.getFields().entrySet()) {
            Object raw = nested.get(fieldEntry.getKey());
            Object value = raw != null ? coerceValue(raw, fieldEntry.getValue()) : defaultValueFor(fieldEntry.getValue());
            result.put(fieldEntry.getKey(), value);
        }

        return result;
    }

    private List<Object> coerceArray(Object raw, ArrayType arrayType) {
        Type<?> elementType = arrayType.getElementType();
        int length = arrayType.getLength();

        Map<Integer, Object> byIndex = raw instanceof TreeMap<?, ?> indexedGroups ? castIndexed(indexedGroups) : Map.of();

        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            Object itemRaw = byIndex.get(i);
            result.add(itemRaw != null ? coerceValue(itemRaw, elementType) : defaultValueFor(elementType));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Object> castIndexed(Map<?, ?> map) {
        return (Map<Integer, Object>) map;
    }

    /**
     * HTML form inputs send every field as a string - the same gotcha {@code
     * ReflectiveFieldApplier#coerce}/{@code RestRequestBodyAssembler#coerceScalar} already handle,
     * coerced here against {@link Type#getJavaType()} instead of a Java target type or OpenAPI
     * type/format pair.
     *
     * <p>NOTE: each branch must return a boxed value explicitly (not via a {@code cond ? long :
     * int} ternary) - Java's conditional-expression numeric promotion silently widens every int32
     * result to {@code Long}, a documented gotcha elsewhere in this codebase
     * ({@code RestRequestBodyAssembler.coerceScalar}).
     */
    private Object coerceScalar(Object raw, Type<?> type) {
        if (!(raw instanceof String stringValue) || type.getJavaType() == null) {
            return raw;
        }

        Class<?> javaType = type.getJavaType();

        if (javaType == Integer.class) {
            return Integer.parseInt(stringValue);
        }
        if (javaType == Long.class) {
            return Long.parseLong(stringValue);
        }
        if (javaType == Float.class) {
            return Float.parseFloat(stringValue);
        }
        if (javaType == Double.class) {
            return Double.parseDouble(stringValue);
        }
        if (javaType == Boolean.class) {
            return Boolean.parseBoolean(stringValue);
        }
        return stringValue;
    }

    private Object defaultValueFor(Type<?> type) {
        if (type instanceof RecordType recordType) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Type<?>> fieldEntry : recordType.getFields().entrySet()) {
                result.put(fieldEntry.getKey(), defaultValueFor(fieldEntry.getValue()));
            }
            return result;
        }

        if (type instanceof ArrayType arrayType) {
            List<Object> result = new ArrayList<>(arrayType.getLength());
            for (int i = 0; i < arrayType.getLength(); i++) {
                result.add(defaultValueFor(arrayType.getElementType()));
            }
            return result;
        }

        Class<?> javaType = type.getJavaType();
        if (javaType == Integer.class) {
            return 0;
        }
        if (javaType == Long.class) {
            return 0L;
        }
        if (javaType == Float.class) {
            return 0f;
        }
        if (javaType == Double.class) {
            return 0.0;
        }
        if (javaType == Boolean.class) {
            return false;
        }
        if (javaType == String.class) {
            return "";
        }
        return null;
    }
}
