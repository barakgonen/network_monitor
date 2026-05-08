package com.example.monitor.reflection;

import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ReflectionFieldExtractor {
    private static final int DEFAULT_MAX_DEPTH = 6;

    public Map<String, Object> extractFields(Object instance) throws Exception {
        return extractObjectFields(
                instance,
                0,
                DEFAULT_MAX_DEPTH,
                new IdentityHashMap<>()
        );
    }

    private Object normalizeValue(
            Object value,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        if (value == null) {
            return null;
        }

        if (isSimpleValue(value)) {
            return value;
        }

        if (value instanceof byte[] bytes) {
            return Map.of(
                    "encoding", "base64",
                    "size", bytes.length,
                    "value", Base64.getEncoder().encodeToString(bytes)
            );
        }

        if (depth >= maxDepth) {
            return Map.of(
                    "_truncated", true,
                    "_reason", "maxDepth=" + maxDepth
            );
        }

        if (visited.containsKey(value)) {
            return Map.of("_cycle", true);
        }

        if (value.getClass().isArray()) {
            return extractArray(value, depth, maxDepth, visited);
        }

        if (value instanceof Collection<?> collection) {
            return extractCollection(collection, depth, maxDepth, visited);
        }

        if (value instanceof Map<?, ?> map) {
            return extractMap(map, depth, maxDepth, visited);
        }

        return extractObjectFields(value, depth + 1, maxDepth, visited);
    }

    private Map<String, Object> extractObjectFields(
            Object instance,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        if (instance == null) {
            return Map.of();
        }

        if (visited.containsKey(instance)) {
            return Map.of("_cycle", true);
        }

        visited.put(instance, Boolean.TRUE);

        try {
            Map<String, Object> fields = new LinkedHashMap<>();

            if (instance.getClass().isRecord()) {
                extractRecordComponents(instance, fields, depth, maxDepth, visited);
            } else {
                extractGetterValues(instance, fields, depth, maxDepth, visited);

                if (fields.isEmpty()) {
                    extractDeclaredFields(instance, fields, depth, maxDepth, visited);
                }
            }

            return fields;
        } finally {
            visited.remove(instance);
        }
    }

    private void extractRecordComponents(
            Object instance,
            Map<String, Object> fields,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        for (RecordComponent component : instance.getClass().getRecordComponents()) {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            Object value = accessor.invoke(instance);
            fields.put(component.getName(), normalizeValue(value, depth + 1, maxDepth, visited));
        }
    }

    private void extractGetterValues(
            Object instance,
            Map<String, Object> fields,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        for (Method method : instance.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }

            String methodName = method.getName();
            String fieldName = null;

            if (methodName.startsWith("get") && methodName.length() > 3 && !"getClass".equals(methodName)) {
                fieldName = decapitalize(methodName.substring(3));
            } else if (methodName.startsWith("is") && methodName.length() > 2) {
                fieldName = decapitalize(methodName.substring(2));
            }

            if (fieldName != null) {
                Object value = method.invoke(instance);
                fields.put(fieldName, normalizeValue(value, depth + 1, maxDepth, visited));
            }
        }
    }

    private void extractDeclaredFields(
            Object instance,
            Map<String, Object> fields,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        Class<?> current = instance.getClass();

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                Object value = field.get(instance);
                fields.put(field.getName(), normalizeValue(value, depth + 1, maxDepth, visited));
            }

            current = current.getSuperclass();
        }
    }

    private List<Object> extractArray(
            Object array,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        int length = Array.getLength(array);
        java.util.ArrayList<Object> values = new java.util.ArrayList<>(length);

        visited.put(array, Boolean.TRUE);

        try {
            for (int i = 0; i < length; i++) {
                values.add(normalizeValue(Array.get(array, i), depth + 1, maxDepth, visited));
            }

            return values;
        } finally {
            visited.remove(array);
        }
    }

    private List<Object> extractCollection(
            Collection<?> collection,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        java.util.ArrayList<Object> values = new java.util.ArrayList<>();

        visited.put(collection, Boolean.TRUE);

        try {
            for (Object item : collection) {
                values.add(normalizeValue(item, depth + 1, maxDepth, visited));
            }

            return values;
        } finally {
            visited.remove(collection);
        }
    }

    private Map<String, Object> extractMap(
            Map<?, ?> map,
            int depth,
            int maxDepth,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        visited.put(map, Boolean.TRUE);

        try {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue(), depth + 1, maxDepth, visited));
            }

            return result;
        } finally {
            visited.remove(map);
        }
    }

    private boolean isSimpleValue(Object value) {
        Class<?> type = value.getClass();

        return type.isPrimitive()
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof TemporalAccessor;
    }

    private String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
