package com.example.schemacore.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reflectively builds a message instance from a generic field map (the inverse of
 * {@link ReflectiveFieldExtractor}), used by the field-map based publish endpoints. Records are
 * built via their canonical constructor; plain classes via a no-arg constructor plus setters.
 * Nested/complex fields arrive as dotted paths (e.g. {@code "header.msgType"}, mirroring
 * {@link com.example.monitor.publisher.PublisherFieldMetadataService}'s flattening) - {@link
 * #unflatten} regroups one level of dotted keys per call, and {@link #coerce} recurses into
 * {@link #build} for nested targets, so arbitrarily deep nesting resolves one level per
 * recursive {@code build} call.
 */
public final class ReflectiveFieldApplier {
    private ReflectiveFieldApplier() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T build(Class<T> type, Map<String, Object> fields) throws Exception {
        Map<String, Object> nested = unflatten(fields);
        if (type.isRecord()) {
            return buildRecord(type, nested);
        }
        return buildViaSetters(type, nested);
    }

    private static Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf('.');
            if (dot < 0) {
                result.put(key, entry.getValue());
                continue;
            }

            String head = key.substring(0, dot);
            String tail = key.substring(dot + 1);
            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) result.computeIfAbsent(head, k -> new LinkedHashMap<String, Object>());
            child.put(tail, entry.getValue());
        }
        return result;
    }

    private static <T> T buildRecord(Class<T> type, Map<String, Object> fields) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            args[i] = coerce(fields.get(components[i].getName()), paramTypes[i]);
        }

        Constructor<T> constructor = type.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static <T> T buildViaSetters(Class<T> type, Map<String, Object> fields) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        T instance = constructor.newInstance();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Method setter = findSetter(type, entry.getKey());
            if (setter != null) {
                setter.invoke(instance, coerce(entry.getValue(), setter.getParameterTypes()[0]));
            }
        }

        return instance;
    }

    private static Method findSetter(Class<?> type, String fieldName) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : type.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(Object value, Class<?> targetType) throws Exception {
        if (value == null) {
            return PrimitiveWireTypes.defaultValue(targetType);
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (value instanceof Map<?, ?> nestedFields) {
            return build(targetType, (Map<String, Object>) nestedFields);
        }

        if (targetType.isEnum() && value instanceof String stringValue) {
            return enumFromWireValue((Class<Enum>) targetType, stringValue);
        }

        if (PrimitiveWireTypes.isNumericType(targetType) && value instanceof String stringValue) {
            return PrimitiveWireTypes.coerceNumber(Double.parseDouble(stringValue), targetType);
        }

        if (value instanceof Number number) {
            return PrimitiveWireTypes.coerceNumber(number, targetType);
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        }

        return value;
    }

    /**
     * Mirrors {@link ReflectiveFieldExtractor}'s enum wire-value convention: if the enum exposes
     * {@code getWireName()}, match case-insensitively against that; otherwise fall back to the
     * Java constant name.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumFromWireValue(Class<? extends Enum> type, String value) {
        Method wireNameMethod;
        try {
            wireNameMethod = type.getMethod("getWireName");
        } catch (NoSuchMethodException e) {
            return Enum.valueOf(type, value);
        }

        try {
            for (Object constant : type.getEnumConstants()) {
                Object wireName = wireNameMethod.invoke(constant);
                if (value.equalsIgnoreCase(String.valueOf(wireName))) {
                    return constant;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed reading getWireName() on " + type.getName(), e);
        }

        return Enum.valueOf(type, value);
    }
}
