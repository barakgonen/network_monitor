package com.example.schemacore;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;

/**
 * Reflectively builds a message instance from a generic field map (the inverse of
 * {@link ReflectiveFieldExtractor}), used by the field-map based publish endpoints. Records are
 * built via their canonical constructor; plain classes via a no-arg constructor plus setters.
 */
public final class ReflectiveFieldApplier {
    private ReflectiveFieldApplier() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T build(Class<T> type, Map<String, Object> fields) throws Exception {
        if (type.isRecord()) {
            return buildRecord(type, fields);
        }
        return buildViaSetters(type, fields);
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
    private static Object coerce(Object value, Class<?> targetType) {
        if (value == null) {
            return defaultValue(targetType);
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (targetType.isEnum() && value instanceof String stringValue) {
            return enumFromWireValue((Class<Enum>) targetType, stringValue);
        }

        if (isNumericType(targetType) && value instanceof String stringValue) {
            return coerceNumber(Double.parseDouble(stringValue), targetType);
        }

        if (value instanceof Number number) {
            return coerceNumber(number, targetType);
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

    private static boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class;
    }

    private static Object coerceNumber(Number number, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return number.intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return number.longValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return number.shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return number.byteValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return number.doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return number.floatValue();
        }
        return number;
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

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) {
            return Boolean.FALSE;
        }
        if (type == char.class) {
            return Character.valueOf((char) 0);
        }
        if (type == byte.class) {
            return Byte.valueOf((byte) 0);
        }
        if (type == short.class) {
            return Short.valueOf((short) 0);
        }
        if (type == int.class) {
            return Integer.valueOf(0);
        }
        if (type == long.class) {
            return Long.valueOf(0L);
        }
        if (type == float.class) {
            return Float.valueOf(0f);
        }
        if (type == double.class) {
            return Double.valueOf(0d);
        }
        return null;
    }
}
