package com.example.schemacore;

import java.util.Map;

public final class MessageFields {

    private MessageFields() {
    }

    public static String requireString(Map<String, Object> fields, String name) {
        Object value = fields == null ? null : fields.get(name);

        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        String stringValue = String.valueOf(value);

        if (stringValue.isBlank()) {
            throw new IllegalArgumentException("Field must not be blank: " + name);
        }

        return stringValue;
    }

    public static double requireDouble(Map<String, Object> fields, String name) {
        Object value = fields == null ? null : fields.get(name);

        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field must be a valid double: " + name + ", value=" + value);
        }
    }

    public static int requireInt(Map<String, Object> fields, String name) {
        Object value = fields == null ? null : fields.get(name);

        if (value == null) {
            throw new IllegalArgumentException("Missing required field: " + name);
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Field must be a valid int: " + name + ", value=" + value);
        }
    }
}
