package com.example.schemautils;

/**
 * Static reflection facade for calculating fixed struct sizes by class name or Class instance.
 */
public final class ReflectionStructSizeCalculator {
    private ReflectionStructSizeCalculator() {
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
        return StructSizeCalculator.calculateStructSize(type);
    }
}
