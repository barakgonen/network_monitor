package com.example.schemacore.reflect;

/**
 * Shared classification/sizing/coercion logic for the 8 boxed-or-primitive scalar types, used by
 * both {@link StructSizeCalculator} (fixed binary layout sizing) and {@link ReflectiveFieldApplier}
 * (generic field-map to typed-message coercion) so the two don't drift out of sync.
 */
final class PrimitiveWireTypes {
    private PrimitiveWireTypes() {
    }

    static boolean isFixedScalar(Class<?> type) {
        return type == byte.class || type == Byte.class
                || type == short.class || type == Short.class
                || type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == boolean.class || type == Boolean.class
                || type == char.class || type == Character.class;
    }

    static int fixedScalarSize(Class<?> type) {
        if (type == byte.class || type == Byte.class || type == boolean.class || type == Boolean.class) {
            return 1;
        }

        if (type == short.class || type == Short.class || type == char.class || type == Character.class) {
            return 2;
        }

        if (type == int.class || type == Integer.class || type == float.class || type == Float.class) {
            return 4;
        }

        if (type == long.class || type == Long.class || type == double.class || type == Double.class) {
            return 8;
        }

        throw new IllegalArgumentException("Unsupported fixed scalar type: " + type.getName());
    }

    static boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class
                || type == byte.class || type == Byte.class
                || type == double.class || type == Double.class
                || type == float.class || type == Float.class;
    }

    static Object coerceNumber(Number number, Class<?> targetType) {
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

    static Object defaultValue(Class<?> type) {
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
