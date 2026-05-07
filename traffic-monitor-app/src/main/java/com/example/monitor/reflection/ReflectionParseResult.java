package com.example.monitor.reflection;

import java.util.Map;

public record ReflectionParseResult(
        boolean parsed,
        String messageClassName,
        String messageSimpleName,
        Object instance,
        Map<String, Object> fields,
        String error
) {
    public static ReflectionParseResult parsed(String className, String simpleName, Object instance, Map<String, Object> fields) {
        return new ReflectionParseResult(true, className, simpleName, instance, fields, null);
    }

    public static ReflectionParseResult unparsable(String error) {
        return new ReflectionParseResult(false, null, "Unparsable", null, Map.of(), error);
    }
}
