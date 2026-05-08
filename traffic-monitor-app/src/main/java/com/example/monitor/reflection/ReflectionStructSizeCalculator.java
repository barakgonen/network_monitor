package com.example.monitor.reflection;

import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

@Component
public class ReflectionStructSizeCalculator {
    public int calculateStructSize(String className) {
        try {
            return calculateStructSize(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }

    public int calculateStructSize(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        return calculate(type, new HashSet<>());
    }

    private int calculate(Class<?> type, Set<Class<?>> visited) {
        if (isPrimitiveOrBoxedFixedSize(type)) {
            return primitiveOrBoxedSize(type);
        }

        if (type.isEnum()) {
            throw new IllegalArgumentException("Cannot infer enum wire size from class alone: " + type.getName());
        }

        if (type == String.class || CharSequence.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("Cannot calculate fixed binary size for variable-length string type: " + type.getName());
        }

        if (type.isArray()) {
            throw new IllegalArgumentException(
                    "Cannot calculate array size from class alone without runtime length or schema metadata: " + type.getName()
            );
        }

        if (visited.contains(type)) {
            throw new IllegalArgumentException("Recursive struct definition detected: " + type.getName());
        }

        visited.add(type);

        try {
            int total = 0;

            Class<?> current = type;

            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }

                    if (Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }

                    total += calculateFieldSize(field, visited);
                }

                current = current.getSuperclass();
            }

            return total;
        } finally {
            visited.remove(type);
        }
    }

    private int calculateFieldSize(Field field, Set<Class<?>> visited) {
        Class<?> fieldType = field.getType();

        if (isPrimitiveOrBoxedFixedSize(fieldType)) {
            return primitiveOrBoxedSize(fieldType);
        }

        if (fieldType.isEnum()) {
            throw new IllegalArgumentException(
                    "Cannot infer enum wire size for field: "
                            + field.getDeclaringClass().getName()
                            + "."
                            + field.getName()
            );
        }

        if (fieldType == String.class || CharSequence.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException(
                    "Cannot calculate fixed binary size for variable-length field: "
                            + field.getDeclaringClass().getName()
                            + "."
                            + field.getName()
            );
        }

        if (fieldType.isArray()) {
            throw new IllegalArgumentException(
                    "Cannot calculate array field size without fixed length metadata: "
                            + field.getDeclaringClass().getName()
                            + "."
                            + field.getName()
            );
        }

        return calculate(fieldType, visited);
    }

    private boolean isPrimitiveOrBoxedFixedSize(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class;
    }

    private int primitiveOrBoxedSize(Class<?> type) {
        if (type == byte.class || type == Byte.class) {
            return Byte.BYTES;
        }

        if (type == short.class || type == Short.class) {
            return Short.BYTES;
        }

        if (type == int.class || type == Integer.class) {
            return Integer.BYTES;
        }

        if (type == long.class || type == Long.class) {
            return Long.BYTES;
        }

        if (type == float.class || type == Float.class) {
            return Float.BYTES;
        }

        if (type == double.class || type == Double.class) {
            return Double.BYTES;
        }

        if (type == boolean.class || type == Boolean.class) {
            return Byte.BYTES;
        }

        if (type == char.class || type == Character.class) {
            return Character.BYTES;
        }

        throw new IllegalArgumentException("Unsupported primitive/fixed type: " + type.getName());
    }
}
