package com.example.monitor.reflection;

import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ReflectionMessageParser {
    public ReflectionParseResult parse(byte[] payload, List<String> potentialMessages) {
        if (potentialMessages == null || potentialMessages.isEmpty()) {
            return ReflectionParseResult.unparsable("No potential message classes were configured");
        }

        Map<String, String> failures = new LinkedHashMap<>();

        for (String className : potentialMessages) {
            try {
                Object instance = instantiate(className, payload);
                Map<String, Object> fields = extractFields(instance);
                return ReflectionParseResult.parsed(className, instance.getClass().getSimpleName(), instance, fields);
            } catch (Exception e) {
                failures.put(className, rootMessage(e));
            }
        }

        return ReflectionParseResult.unparsable("No potential message class matched payload. failures=" + failures);
    }

    private Object instantiate(String className, byte[] payload) throws Exception {
        Class<?> type = Class.forName(className);

        try {
            Constructor<?> constructor = type.getDeclaredConstructor(byte[].class);
            constructor.setAccessible(true);
            return constructor.newInstance((Object) payload);
        } catch (NoSuchMethodException ignored) {
        }

        for (String methodName : List.of("parse", "fromBytes", "fromByteArray")) {
            try {
                Method method = type.getDeclaredMethod(methodName, byte[].class);

                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }

                method.setAccessible(true);
                Object value = method.invoke(null, (Object) payload);

                if (value == null) {
                    throw new IllegalArgumentException("Factory method returned null: " + methodName);
                }

                return value;
            } catch (NoSuchMethodException ignored) {
            }
        }

        throw new IllegalArgumentException(
                "Expected constructor(byte[]) or static parse/fromBytes/fromByteArray(byte[]) in " + className
        );
    }

    private Map<String, Object> extractFields(Object instance) throws Exception {
        if (instance.getClass().isRecord()) {
            return extractRecordComponents(instance);
        }

        Map<String, Object> fields = new LinkedHashMap<>();
        extractGetterValues(instance, fields);

        if (fields.isEmpty()) {
            extractDeclaredFields(instance, fields);
        }

        return fields;
    }

    private Map<String, Object> extractRecordComponents(Object instance) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();

        for (RecordComponent component : instance.getClass().getRecordComponents()) {
            Method accessor = component.getAccessor();
            accessor.setAccessible(true);
            fields.put(component.getName(), normalizeValue(accessor.invoke(instance)));
        }

        return fields;
    }

    private void extractGetterValues(Object instance, Map<String, Object> fields) throws Exception {
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
                fields.put(fieldName, normalizeValue(method.invoke(instance)));
            }
        }
    }

    private void extractDeclaredFields(Object instance, Map<String, Object> fields) throws Exception {
        Class<?> current = instance.getClass();

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }

                field.setAccessible(true);
                fields.put(field.getName(), normalizeValue(field.get(instance)));
            }

            current = current.getSuperclass();
        }
    }

    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String || value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return value;
        }

        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }

        return String.valueOf(value);
    }

    private String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }

    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
