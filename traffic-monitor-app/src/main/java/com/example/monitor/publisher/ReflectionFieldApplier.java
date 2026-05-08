package com.example.monitor.publisher;

import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

@Component
public class ReflectionFieldApplier {
    public Object createAndApply(String className, Map<String, Object> fields) {
        try {
            Object instance = instantiate(Class.forName(className));
            apply(instance, fields);
            return instance;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed creating message " + className + ": " + rootMessage(e), e);
        }
    }

    public void apply(Object target, Map<String, Object> fields) {
        if (target == null || fields == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            applyField(target, entry.getKey(), entry.getValue());
        }
    }

    private void applyField(Object target, String fieldName, Object rawValue) {
        try {
            Field field = findField(target.getClass(), fieldName);

            if (field == null) {
                throw new IllegalArgumentException("Unknown field: " + target.getClass().getName() + "." + fieldName);
            }

            field.setAccessible(true);
            Object convertedValue = convertValue(field.getType(), field.get(target), rawValue);
            field.set(target, convertedValue);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed applying field "
                            + target.getClass().getName()
                            + "."
                            + fieldName
                            + ": "
                            + rootMessage(e),
                    e
            );
        }
    }

    private Object convertValue(Class<?> targetType, Object currentValue, Object rawValue) {
        if (rawValue == null) {
            if (targetType.isPrimitive()) {
                throw new IllegalArgumentException("Cannot assign null to primitive " + targetType.getName());
            }

            return null;
        }

        if (targetType == String.class) {
            return String.valueOf(rawValue);
        }

        if (targetType == byte.class || targetType == Byte.class) {
            return asNumber(rawValue).byteValue();
        }

        if (targetType == short.class || targetType == Short.class) {
            return asNumber(rawValue).shortValue();
        }

        if (targetType == int.class || targetType == Integer.class) {
            return asNumber(rawValue).intValue();
        }

        if (targetType == long.class || targetType == Long.class) {
            return asNumber(rawValue).longValue();
        }

        if (targetType == float.class || targetType == Float.class) {
            return asNumber(rawValue).floatValue();
        }

        if (targetType == double.class || targetType == Double.class) {
            return asNumber(rawValue).doubleValue();
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (rawValue instanceof Boolean booleanValue) {
                return booleanValue;
            }

            return Boolean.parseBoolean(String.valueOf(rawValue));
        }

        if (targetType == char.class || targetType == Character.class) {
            String value = String.valueOf(rawValue);

            if (value.length() != 1) {
                throw new IllegalArgumentException("Expected single character for " + targetType.getName());
            }

            return value.charAt(0);
        }

        if (targetType.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object enumValue = Enum.valueOf((Class<? extends Enum>) targetType.asSubclass(Enum.class), String.valueOf(rawValue));
            return enumValue;
        }

        if (targetType.isArray()) {
            return convertArrayValue(targetType, currentValue, rawValue);
        }

        if (rawValue instanceof Map<?, ?> mapValue) {
            Object nested = currentValue != null ? currentValue : instantiate(targetType);
            apply(nested, castMap(mapValue));
            return nested;
        }

        throw new IllegalArgumentException("Unsupported field type: " + targetType.getName() + ", rawValue=" + rawValue);
    }

    private Object convertArrayValue(Class<?> targetType, Object currentValue, Object rawValue) {
        if (!(rawValue instanceof List<?> listValue)) {
            throw new IllegalArgumentException("Array field expects JSON array, got: " + rawValue.getClass().getName());
        }

        Class<?> componentType = targetType.getComponentType();
        Object array = currentValue;

        if (array == null || Array.getLength(array) != listValue.size()) {
            array = Array.newInstance(componentType, listValue.size());
        }

        for (int i = 0; i < listValue.size(); i++) {
            Object existing = Array.get(array, i);
            Object rawItem = listValue.get(i);

            if (rawItem instanceof Map<?, ?> rawMap && !isSimpleType(componentType)) {
                Object nested = existing != null ? existing : instantiate(componentType);
                apply(nested, castMap(rawMap));
                Array.set(array, i, nested);
            } else {
                Object converted = convertValue(componentType, existing, rawItem);
                Array.set(array, i, converted);
            }
        }

        return array;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class
                || type.isEnum();
    }

    private Number asNumber(Object rawValue) {
        if (rawValue instanceof Number number) {
            return number;
        }

        String value = String.valueOf(rawValue);

        if (value.contains(".")) {
            return new BigDecimal(value);
        }

        return new BigInteger(value);
    }

    private Object instantiate(Class<?> type) {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class must expose a no-arg constructor for publishing: " + type.getName(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed instantiating " + type.getName() + ": " + rootMessage(e), e);
        }
    }

    private Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;

        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private String rootMessage(Exception e) {
        Throwable current = e;

        while (current.getCause() != null) {
            current = current.getCause();
        }

        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
