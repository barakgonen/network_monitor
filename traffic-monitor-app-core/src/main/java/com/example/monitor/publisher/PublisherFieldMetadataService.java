package com.example.monitor.publisher;

import com.example.schemacore.reflect.AccessorNames;
import com.example.schemacore.reflect.FixedArrayLengths;
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
 * into nested objects when building a message from submitted values. Arrays of structs (e.g.
 * {@code trackData}) are described instead via {@link PublisherFieldDto#itemFields()} - one
 * element's shape, rather than being recursed into directly, since the number of elements the
 * user wants to submit isn't known until they build the form - the UI renders an "add row"
 * control per {@link PublisherFieldDto#maxLength()} instead of a fixed set of dotted paths.
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
                describeField(type, component.getName(), component.getType(), prefix, depth, out);
            }
            return;
        }

        for (Method method : type.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }

            String fieldName = AccessorNames.fromAccessor(method.getName());
            if (fieldName != null) {
                describeField(type, fieldName, method.getReturnType(), prefix, depth, out);
            }
        }
    }

    private void describeField(Class<?> owner, String name, Class<?> fieldType, String prefix, int depth, List<PublisherFieldDto> out) {
        String qualifiedName = prefix.isEmpty() ? name : prefix + "." + name;

        if (fieldType.isArray() && depth < MAX_DEPTH && isComplex(fieldType.getComponentType())) {
            describeArrayField(owner, name, qualifiedName, fieldType.getComponentType(), depth, out);
            return;
        }

        if (depth < MAX_DEPTH && isComplex(fieldType)) {
            describeFields(fieldType, qualifiedName, depth + 1, out);
            return;
        }

        out.add(new PublisherFieldDto(qualifiedName, fieldType.getSimpleName()));
    }

    private void describeArrayField(Class<?> owner, String fieldName, String qualifiedName, Class<?> componentType, int depth, List<PublisherFieldDto> out) {
        List<PublisherFieldDto> itemFields = new ArrayList<>();
        describeFields(componentType, "", depth + 1, itemFields);
        Integer maxLength = FixedArrayLengths.find(owner, fieldName).orElse(null);
        out.add(new PublisherFieldDto(qualifiedName, componentType.getSimpleName() + "[]", itemFields, maxLength));
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
