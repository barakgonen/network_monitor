package com.example.schemacore.reflect;

/**
 * The "getX/isX no-arg accessor -> field name x" convention shared by {@link ReflectiveFieldExtractor}
 * (reading message field values) and the generic publisher UI's field metadata (describing message
 * fields without an instance), so both stay in sync on what counts as an accessor.
 */
public final class AccessorNames {
    private AccessorNames() {
    }

    /**
     * Returns the field name for a no-arg {@code getX}/{@code isX} accessor method name, or
     * {@code null} if {@code methodName} doesn't follow that convention.
     */
    public static String fromAccessor(String methodName) {
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
