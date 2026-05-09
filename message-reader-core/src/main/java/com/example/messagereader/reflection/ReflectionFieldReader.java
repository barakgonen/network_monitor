package com.example.messagereader.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionFieldReader {
    public Object readField(Object target, String fieldName) {
        if (target == null) {
            throw new IllegalArgumentException("Cannot read field from null target");
        }

        if (fieldName == null || fieldName.isBlank()) {
            throw new IllegalArgumentException("fieldName is required");
        }

        try {
            Method getter = findGetter(target.getClass(), fieldName);

            if (getter != null) {
                getter.setAccessible(true);
                return getter.invoke(target);
            }

            Field field = findField(target.getClass(), fieldName);

            if (field == null) {
                throw new IllegalArgumentException("Field not found: " + target.getClass().getName() + "." + fieldName);
            }

            field.setAccessible(true);
            return field.get(target);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed reading field " + fieldName + " from " + target.getClass().getName(), e);
        }
    }

    private Method findGetter(Class<?> type, String fieldName) {
        String suffix = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);

        for (String methodName : java.util.List.of("get" + suffix, "is" + suffix, fieldName)) {
            try {
                Method method = type.getMethod(methodName);

                if (method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException ignored) {
            }
        }

        return null;
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
}
