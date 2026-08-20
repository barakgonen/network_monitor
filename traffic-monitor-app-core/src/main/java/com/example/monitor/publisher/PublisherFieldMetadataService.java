package com.example.monitor.publisher;

import com.example.schemacore.reflect.AccessorNames;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Reflects a message class's fields (name + simple type) without needing an instance, so the
 * generic publisher UI can build an editable form before the user has entered any values.
 * Complex/nested fields (e.g. an embedded header struct) are recursed into and flattened to
 * dotted paths (e.g. {@code "header.msgType"}) rather than listed as a single opaque field -
 * {@link com.example.schemacore.reflect.ReflectiveFieldApplier} regroups those dotted paths back
 * into nested objects when building a message from submitted values.
 */
@Component
public class PublisherFieldMetadataService {

    private static final int MAX_DEPTH = 6;

    public List<PublisherFieldDto> describeFields(Class<?> messageClass) {
        List<PublisherFieldDto> fields = new ArrayList<>();
        describeFields(messageClass, "", 0, fields);
        return fields;
    }

    private void describeFields(Class<?> type, String prefix, int depth, List<PublisherFieldDto> out) {
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                describeField(component.getName(), component.getType(), prefix, depth, out);
            }
            return;
        }

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }

            String fieldName = AccessorNames.fromAccessor(method.getName());
            if (fieldName != null) {
                describeField(fieldName, method.getReturnType(), prefix, depth, out);
            }
        }
    }

    private void describeField(String name, Class<?> fieldType, String prefix, int depth, List<PublisherFieldDto> out) {
        String qualifiedName = prefix.isEmpty() ? name : prefix + "." + name;

        if (depth < MAX_DEPTH && isComplex(fieldType)) {
            describeFields(fieldType, qualifiedName, depth + 1, out);
            return;
        }

        out.add(new PublisherFieldDto(qualifiedName, fieldType.getSimpleName()));
    }

    /**
     * Mirrors {@link com.example.schemacore.reflect.ReflectiveFieldExtractor}'s notion of a
     * "simple" (leaf) value - anything else is a struct worth recursing into.
     */
    private boolean isComplex(Class<?> type) {
        return !(type.isPrimitive()
                || type.isEnum()
                || type.isArray()
                || type == String.class
                || Number.class.isAssignableFrom(type)
                || type == Boolean.class
                || type == Character.class);
    }
}
