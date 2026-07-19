package com.example.schemacore;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reflectively converts a decoded message/header object into a generic field map, for archival,
 * analytics, and the generic publisher UI. Records are read via their components; plain classes
 * via their getters.
 */
public final class ReflectiveFieldExtractor {
    private static final int MAX_DEPTH = 6;

    private ReflectiveFieldExtractor() {
    }

    public static Map<String, Object> extractFields(Object instance) throws Exception {
        return extract(instance, 0);
    }

    private static Map<String, Object> extract(Object instance, int depth) throws Exception {
        Map<String, Object> fields = new LinkedHashMap<>();

        if (instance == null) {
            return fields;
        }

        Class<?> type = instance.getClass();

        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                Object value = component.getAccessor().invoke(instance);
                fields.put(component.getName(), normalize(value, depth));
            }
            return fields;
        }

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }

            String fieldName = accessorFieldName(method.getName());
            if (fieldName == null) {
                continue;
            }

            Object value = method.invoke(instance);
            fields.put(fieldName, normalize(value, depth));
        }

        return fields;
    }

    private static Object normalize(Object value, int depth) throws Exception {
        if (value == null) {
            return null;
        }

        if (value instanceof Enum<?> enumValue) {
            return enumWireValue(enumValue);
        }

        if (isSimple(value)) {
            return value;
        }

        if (depth >= MAX_DEPTH) {
            return null;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(normalize(Array.get(value, i), depth + 1));
            }
            return list;
        }

        return extract(value, depth + 1);
    }

    /**
     * Enums with a {@code getWireName()} accessor (e.g. protocol enums whose wire encoding isn't
     * their Java constant name) are represented by that value; all others fall back to {@code name()}.
     */
    private static Object enumWireValue(Enum<?> enumValue) throws Exception {
        try {
            Method wireNameMethod = enumValue.getClass().getMethod("getWireName");
            if (wireNameMethod.getReturnType() == String.class) {
                return wireNameMethod.invoke(enumValue);
            }
        } catch (NoSuchMethodException ignored) {
        }

        return enumValue.name();
    }

    private static boolean isSimple(Object value) {
        return value instanceof Number || value instanceof String || value instanceof Boolean || value instanceof Character;
    }

    private static String accessorFieldName(String methodName) {
        if (methodName.startsWith("get") && methodName.length() > 3) {
            return decapitalize(methodName.substring(3));
        }
        if (methodName.startsWith("is") && methodName.length() > 2) {
            return decapitalize(methodName.substring(2));
        }
        return null;
    }

    private static String decapitalize(String value) {
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}
