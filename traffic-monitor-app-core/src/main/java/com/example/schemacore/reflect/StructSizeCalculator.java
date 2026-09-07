package com.example.schemacore.reflect;

import com.example.schemacore.annotation.EnumWireSize;
import com.example.schemacore.annotation.FixedArrayLength;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

/**
 * Static utility for calculating fixed binary struct sizes from Java class layout.
 *
 * Supported:
 * - primitive fields
 * - boxed primitive fields
 * - enum fields
 * - nested fixed-size structs
 * - fixed-size arrays using {@link FixedArrayLength}
 *
 * Enum behavior:
 * - By default, enum fields are treated as 4 bytes.
 * - Override per field with {@link EnumWireSize}.
 *
 * Unsupported by design:
 * - String / CharSequence
 * - collections / maps
 * - arrays without {@link FixedArrayLength}
 */
public final class StructSizeCalculator {
    private static final int DEFAULT_ENUM_WIRE_SIZE_BYTES = Integer.BYTES;

    private StructSizeCalculator() {
    }

    public static int calculateStructSize(String className) {
        if (className == null || className.isBlank()) {
            throw new IllegalArgumentException("className is required");
        }

        try {
            return calculateStructSize(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Class not found: " + className, e);
        }
    }

    public static int calculateStructSize(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }

        return calculate(type, new HashSet<>());
    }

    private static int calculate(Class<?> type, Set<Class<?>> visiting) {
        if (isFixedScalar(type)) {
            return fixedScalarSize(type);
        }

        if (type.isEnum()) {
            return DEFAULT_ENUM_WIRE_SIZE_BYTES;
        }

        if (type == String.class || CharSequence.class.isAssignableFrom(type)) {
            throw new IllegalArgumentException("String is variable length: " + type.getName());
        }

        if (type.isArray()) {
            throw new IllegalArgumentException("Top-level arrays are not supported. Use a field with @FixedArrayLength.");
        }

        if (!visiting.add(type)) {
            throw new IllegalStateException("Recursive struct detected: " + type.getName());
        }

        try {
            int total = 0;
            Class<?> current = type;

            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }

                    total += calculateFieldSize(field, visiting);
                }

                current = current.getSuperclass();
            }

            return total;
        } finally {
            visiting.remove(type);
        }
    }

    private static int calculateFieldSize(Field field, Set<Class<?>> visiting) {
        Class<?> fieldType = field.getType();

        if (isFixedScalar(fieldType)) {
            return fixedScalarSize(fieldType);
        }

        if (fieldType.isArray()) {
            return calculateArrayFieldSize(field, visiting);
        }

        if (fieldType.isEnum()) {
            return enumWireSize(field);
        }

        if (fieldType == String.class || CharSequence.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException("String is variable length for field: " + fieldDescription(field));
        }

        return calculate(fieldType, visiting);
    }

    private static int calculateArrayFieldSize(Field field, Set<Class<?>> visiting) {
        int length = resolveArrayLength(field);

        if (length < 0) {
            throw new IllegalArgumentException("@FixedArrayLength must be >= 0: " + fieldDescription(field));
        }

        Class<?> componentType = field.getType().getComponentType();

        if (componentType.isArray()) {
            throw new IllegalArgumentException("Nested arrays are not supported: " + fieldDescription(field));
        }

        int componentSize;

        if (isFixedScalar(componentType)) {
            componentSize = fixedScalarSize(componentType);
        } else if (componentType.isEnum()) {
            componentSize = enumWireSize(field);
        } else if (componentType == String.class || CharSequence.class.isAssignableFrom(componentType)) {
            throw new IllegalArgumentException("String array component is variable length: " + fieldDescription(field));
        } else {
            componentSize = calculate(componentType, visiting);
        }

        return length * componentSize;
    }

    /**
     * Prefers the declared {@link FixedArrayLength}; when absent (e.g. a class whose array
     * fields are sized only by their inline initializer, like {@code RadaTracksExtended}), falls
     * back to instantiating the field's declaring class via its no-arg constructor and reading
     * the actual length of the array that constructor already built. Public so callers outside
     * this package (e.g. {@code serdes-generator}'s {@code ProtocolJsonGenerator}) resolve array
     * length the exact same way this class does, rather than duplicating the fallback.
     */
    public static int resolveArrayLength(Field field) {
        FixedArrayLength fixedArrayLength = field.getAnnotation(FixedArrayLength.class);
        if (fixedArrayLength != null) {
            return fixedArrayLength.value();
        }
        return inferArrayLengthFromDefaultInstance(field);
    }

    private static int inferArrayLengthFromDefaultInstance(Field field) {
        Class<?> owner = field.getDeclaringClass();
        Object instance;
        try {
            Constructor<?> constructor = owner.getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(
                    "Array field missing @FixedArrayLength and " + owner.getName()
                            + " has no accessible no-arg constructor to infer its length from: "
                            + fieldDescription(field), e);
        }

        Object value;
        try {
            field.setAccessible(true);
            value = field.get(instance);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected reflection failure reading " + fieldDescription(field), e);
        }

        if (value == null) {
            throw new IllegalArgumentException(
                    "Array field missing @FixedArrayLength and was not initialized by "
                            + owner.getName() + "'s no-arg constructor: " + fieldDescription(field));
        }

        return Array.getLength(value);
    }

    private static int enumWireSize(Field field) {
        EnumWireSize enumWireSize = field.getAnnotation(EnumWireSize.class);

        if (enumWireSize == null) {
            return DEFAULT_ENUM_WIRE_SIZE_BYTES;
        }

        int value = enumWireSize.value();

        if (value == Byte.BYTES
                || value == Short.BYTES
                || value == Integer.BYTES
                || value == Long.BYTES) {
            return value;
        }

        throw new IllegalArgumentException(
                "@EnumWireSize must be one of 1, 2, 4, 8 bytes for field: "
                        + fieldDescription(field)
                        + ", actual="
                        + value
        );
    }

    public static boolean isFixedScalar(Class<?> type) {
        return PrimitiveWireTypes.isFixedScalar(type);
    }

    public static int fixedScalarSize(Class<?> type) {
        return PrimitiveWireTypes.fixedScalarSize(type);
    }

    private static String fieldDescription(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }
}
