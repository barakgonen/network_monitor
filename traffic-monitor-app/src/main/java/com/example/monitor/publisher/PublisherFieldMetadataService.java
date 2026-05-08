package com.example.monitor.publisher;

import com.example.monitor.api.publisher.PublisherFieldDto;
import org.springframework.stereotype.Service;

import java.lang.reflect.Constructor;
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
            Object instance = tryInstantiate(type);
            return fieldsForType(type, instance, "", 0);
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
                            List.of(),
                            null
                    )),
                    null
            ));
        }
    }

    private List<PublisherFieldDto> fieldsForType(Class<?> type, Object instance, String parentPath, int depth) {
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

                result.add(fieldDto(field, instance, parentPath, depth));
            }

            current = current.getSuperclass();
        }

        return result;
    }

    private PublisherFieldDto fieldDto(Field field, Object parentInstance, String parentPath, int depth) {
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
                    List.of(),
                    null
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
            return arrayField(field, parentInstance, path, depth);
        }

        if (isComplex(fieldType)) {
            Object nestedInstance = currentFieldValue(field, parentInstance);
            if (nestedInstance == null) {
                nestedInstance = tryInstantiate(fieldType);
            }

            return new PublisherFieldDto(
                    name,
                    path,
                    "object",
                    fieldType.getName(),
                    false,
                    List.of(),
                    fieldsForType(fieldType, nestedInstance, path, depth + 1),
                    null
            );
        }

        return new PublisherFieldDto(
                name,
                path,
                "unsupported",
                fieldType.getName(),
                false,
                List.of(),
                List.of(),
                null
        );
    }

    private PublisherFieldDto arrayField(Field field, Object parentInstance, String path, int depth) {
        Class<?> fieldType = field.getType();
        Class<?> componentType = fieldType.getComponentType();

        Integer arrayLength = inferArrayLength(field, parentInstance);
        List<PublisherFieldDto> children = List.of();

        if (isComplex(componentType)) {
            Object componentInstance = tryInstantiate(componentType);
            children = fieldsForType(componentType, componentInstance, path + "[]", depth + 1);
        } else if (componentType.isEnum()) {
            children = List.of(new PublisherFieldDto(
                    "value",
                    path + "[]",
                    "enum",
                    componentType.getName(),
                    componentType.isPrimitive(),
                    enumValues(componentType),
                    List.of(),
                    null
            ));
        } else if (isString(componentType)) {
            children = List.of(new PublisherFieldDto(
                    "value",
                    path + "[]",
                    "string",
                    componentType.getName(),
                    false,
                    List.of(),
                    List.of(),
                    null
            ));
        } else if (isNumber(componentType)) {
            children = List.of(new PublisherFieldDto(
                    "value",
                    path + "[]",
                    "number",
                    componentType.getName(),
                    componentType.isPrimitive(),
                    List.of(),
                    List.of(),
                    null
            ));
        } else if (isBoolean(componentType)) {
            children = List.of(new PublisherFieldDto(
                    "value",
                    path + "[]",
                    "boolean",
                    componentType.getName(),
                    componentType.isPrimitive(),
                    List.of(),
                    List.of(),
                    null
            ));
        }

        return new PublisherFieldDto(
                field.getName(),
                path,
                "array",
                fieldType.getName(),
                false,
                componentType.isEnum() ? enumValues(componentType) : List.of(),
                children,
                arrayLength
        );
    }

    private Integer inferArrayLength(Field field, Object parentInstance) {
        Object current = currentFieldValue(field, parentInstance);

        if (current != null && current.getClass().isArray()) {
            return java.lang.reflect.Array.getLength(current);
        }

        return null;
    }

    private Object currentFieldValue(Field field, Object parentInstance) {
        if (parentInstance == null) {
            return null;
        }

        try {
            field.setAccessible(true);
            return field.get(parentInstance);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object tryInstantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception ignored) {
            return null;
        }
    }

    private PublisherFieldDto scalar(String name, String path, String kind, Class<?> type) {
        return new PublisherFieldDto(
                name,
                path,
                kind,
                type.getName(),
                type.isPrimitive(),
                List.of(),
                List.of(),
                null
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
