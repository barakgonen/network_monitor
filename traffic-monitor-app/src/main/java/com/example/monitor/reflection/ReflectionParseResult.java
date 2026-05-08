package com.example.monitor.reflection;

import java.util.Map;

public record ReflectionParseResult(
        boolean parsed,
        String messageClassName,
        String messageSimpleName,
        Object instance,
        Map<String, Object> headerFields,
        Map<String, Object> fields,
        Object opcode,
        String error
) {
    public static ReflectionParseResult parsed(
            String className,
            String simpleName,
            Object instance,
            Map<String, Object> headerFields,
            Map<String, Object> fields,
            Object opcode
    ) {
        return new ReflectionParseResult(true, className, simpleName, instance, headerFields, fields, opcode, null);
    }

    public static ReflectionParseResult unparsable(String error) {
        return new ReflectionParseResult(false, null, "Unparsable", null, Map.of(), Map.of(), null, error);
    }
}
