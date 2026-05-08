package com.example.monitor.publisher;

import com.example.monitor.api.publisher.PublisherFieldDto;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class PublisherFieldMetadataService {
    private static final int MAX_DEPTH = 5;

    public List<PublisherFieldDto> fieldsForClass(String className) {
        try {
            Class<?> type = Class.forName(className);
            return fieldsForType(type, "", 0);
        } catch (Exception e) {
            return List.of(new PublisherFieldDto(
                    "_metadataError",
                    "_metadataError",
                    "error",
                    "java.lang.String",
                    false,
                    List.of(),
                    List.of(new PublisherFieldDto(
                            "message",
                            "_metadataError.message",
                            "string",
                            "java.lang.String",
                            false,
                            List.of(),
                            List.of()
                    ))
            ));
        }
    }

    private List<PublisherFieldDto> fieldsForType(Class<?> type, String parentPath, int depth) {
        if (depth > MAX_DEPTH) {
            return List.of();
        }

        List<PublisherFieldDto> result = new ArrayList<>();
        Class<?> current = type;

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                result.add(fieldDto(field, parentPath, depth));
            }

            current = current.getSuperclass();
        }

        return result;
    }

    private PublisherFieldDto fieldDto(Field field, String parentPath, int depth) {
        Class<?> fieldType = field.getType();
        String name = field.getName();
        String path = parentPath == null || parentPath.isBlank() ? name : parentPath + "." + name;

        if (fieldType.isEnum()) {
            return new PublisherFieldDto(
                    name,
                    path,
                    "enum",
                    fieldType.getName(),
                    fieldType.isPrimitive(),
                    enumValues(fieldType),
                    List.of()
            );
        }

        if (isString(fieldType)) {
            return scalar(name, path, "string", fieldType);
        }

        if (isNumber(fieldType)) {
            return scalar(name, path, "number", fieldType);
        }

        if (isBoolean(fieldType)) {
            return scalar(name, path, "boolean", fieldType);
        }

        if (fieldType == char.class || fieldType == Character.class) {
            return scalar(name, path, "char", fieldType);
        }

        if (fieldType.isArray()) {
            Class<?> componentType = fieldType.getComponentType();
            List<PublisherFieldDto> children = isComplex(componentType)
                    ? fieldsForType(componentType, path + "[]", depth + 1)
                    : List.of();

            return new PublisherFieldDto(
                    name,
                    path,
                    "array",
                    fieldType.getName(),
                    false,
                    componentType.isEnum() ? enumValues(componentType) : List.of(),
                    children
            );
        }

        if (isComplex(fieldType)) {
            return new PublisherFieldDto(
                    name,
                    path,
                    "object",
                    fieldType.getName(),
                    false,
                    List.of(),
                    fieldsForType(fieldType, path, depth + 1)
            );
        }

        return new PublisherFieldDto(
                name,
                path,
                "unsupported",
                fieldType.getName(),
                false,
                List.of(),
                List.of()
        );
    }

    private PublisherFieldDto scalar(String name, String path, String kind, Class<?> type) {
        return new PublisherFieldDto(
                name,
                path,
                kind,
                type.getName(),
                type.isPrimitive(),
                List.of(),
                List.of()
        );
    }

    private List<String> enumValues(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();

        if (constants == null) {
            return List.of();
        }

        List<String> values = new ArrayList<>();

        for (Object constant : constants) {
            values.add(String.valueOf(constant));
        }

        return values;
    }

    private boolean isString(Class<?> type) {
        return type == String.class || CharSequence.class.isAssignableFrom(type);
    }

    private boolean isNumber(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || Number.class.isAssignableFrom(type)
                || type == BigDecimal.class
                || type == BigInteger.class;
    }

    private boolean isBoolean(Class<?> type) {
        return type == boolean.class || type == Boolean.class;
    }

    private boolean isComplex(Class<?> type) {
        return !type.isPrimitive()
                && !type.isEnum()
                && !isString(type)
                && !isNumber(type)
                && !isBoolean(type)
                && type != Character.class
                && type != UUID.class
                && !TemporalAccessor.class.isAssignableFrom(type);
    }
}
