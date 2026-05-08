package com.example.monitor.reflection;

import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.OptionalLong;

@Component
public class ParsedByteSizeCalculator {
    public OptionalLong calculate(Object parsedMessage) {
        if (parsedMessage == null) {
            return OptionalLong.empty();
        }

        try {
            return calculateObject(parsedMessage, new IdentityHashMap<>());
        } catch (Exception e) {
            return OptionalLong.empty();
        }
    }

    private OptionalLong calculateObject(Object value, IdentityHashMap<Object, Boolean> visited) throws Exception {
        if (value == null) {
            return OptionalLong.empty();
        }

        if (visited.containsKey(value)) {
            return OptionalLong.empty();
        }

        Class<?> type = value.getClass();

        if (isKnownScalarType(type)) {
            return scalarSize(type);
        }

        if (type.isEnum()) {
            return OptionalLong.empty();
        }

        if (type == String.class || CharSequence.class.isAssignableFrom(type)) {
            return OptionalLong.empty();
        }

        if (type.isArray()) {
            return calculateArray(value, visited);
        }

        visited.put(value, Boolean.TRUE);

        try {
            long total = 0;
            Class<?> current = type;

            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    field.setAccessible(true);

                    OptionalLong fieldSize = calculateField(field, field.get(value), visited);

                    if (fieldSize.isEmpty()) {
                        return OptionalLong.empty();
                    }

                    total += fieldSize.getAsLong();
                }

                current = current.getSuperclass();
            }

            return OptionalLong.of(total);
        } finally {
            visited.remove(value);
        }
    }

    private OptionalLong calculateField(
            Field field,
            Object fieldValue,
            IdentityHashMap<Object, Boolean> visited
    ) throws Exception {
        Class<?> declaredType = field.getType();

        if (declaredType.isPrimitive()) {
            return primitiveSize(declaredType);
        }

        if (isKnownScalarType(declaredType)) {
            return scalarSize(declaredType);
        }

        if (declaredType.isEnum()) {
            return OptionalLong.empty();
        }

        if (declaredType == String.class || CharSequence.class.isAssignableFrom(declaredType)) {
            return OptionalLong.empty();
        }

        if (declaredType.isArray()) {
            if (fieldValue == null) {
                return OptionalLong.empty();
            }

            return calculateArray(fieldValue, visited);
        }

        if (fieldValue == null) {
            return OptionalLong.empty();
        }

        return calculateObject(fieldValue, visited);
    }

    private OptionalLong calculateArray(Object array, IdentityHashMap<Object, Boolean> visited) throws Exception {
        Class<?> componentType = array.getClass().getComponentType();
        int length = Array.getLength(array);

        if (componentType.isPrimitive()) {
            OptionalLong componentSize = primitiveSize(componentType);

            if (componentSize.isEmpty()) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(componentSize.getAsLong() * length);
        }

        if (isKnownScalarType(componentType)) {
            OptionalLong componentSize = scalarSize(componentType);

            if (componentSize.isEmpty()) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(componentSize.getAsLong() * length);
        }

        long total = 0;

        for (int i = 0; i < length; i++) {
            Object element = Array.get(array, i);
            OptionalLong elementSize = calculateObject(element, visited);

            if (elementSize.isEmpty()) {
                return OptionalLong.empty();
            }

            total += elementSize.getAsLong();
        }

        return OptionalLong.of(total);
    }

    private boolean isKnownScalarType(Class<?> type) {
        return type == Byte.class
                || type == Short.class
                || type == Integer.class
                || type == Long.class
                || type == Float.class
                || type == Double.class
                || type == Character.class
                || type == Boolean.class;
    }

    private OptionalLong scalarSize(Class<?> type) {
        if (type == Byte.class) {
            return OptionalLong.of(Byte.BYTES);
        }

        if (type == Short.class) {
            return OptionalLong.of(Short.BYTES);
        }

        if (type == Integer.class) {
            return OptionalLong.of(Integer.BYTES);
        }

        if (type == Long.class) {
            return OptionalLong.of(Long.BYTES);
        }

        if (type == Float.class) {
            return OptionalLong.of(Float.BYTES);
        }

        if (type == Double.class) {
            return OptionalLong.of(Double.BYTES);
        }

        if (type == Character.class) {
            return OptionalLong.of(Character.BYTES);
        }

        if (type == Boolean.class) {
            return OptionalLong.of(Byte.BYTES);
        }

        return OptionalLong.empty();
    }

    private OptionalLong primitiveSize(Class<?> type) {
        if (type == byte.class) {
            return OptionalLong.of(Byte.BYTES);
        }

        if (type == short.class) {
            return OptionalLong.of(Short.BYTES);
        }

        if (type == int.class) {
            return OptionalLong.of(Integer.BYTES);
        }

        if (type == long.class) {
            return OptionalLong.of(Long.BYTES);
        }

        if (type == float.class) {
            return OptionalLong.of(Float.BYTES);
        }

        if (type == double.class) {
            return OptionalLong.of(Double.BYTES);
        }

        if (type == char.class) {
            return OptionalLong.of(Character.BYTES);
        }

        if (type == boolean.class) {
            return OptionalLong.of(Byte.BYTES);
        }

        return OptionalLong.empty();
    }
}
