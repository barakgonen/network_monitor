package com.example.schemacore.reflect;

import com.example.schemacore.annotation.FixedArrayLength;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Looks up a declared field's {@link FixedArrayLength} annotation by name, walking up the
 * class hierarchy. Shared by {@link com.example.monitor.publisher.PublisherFieldMetadataService}
 * (to report the array's capacity to the generic publisher UI) and {@link ReflectiveFieldApplier}
 * (to pad a partially-populated array up to its wire-mandated length).
 */
public final class FixedArrayLengths {
    private FixedArrayLengths() {
    }

    public static Optional<Integer> find(Class<?> owner, String fieldName) {
        for (Class<?> current = owner; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(fieldName);
                FixedArrayLength annotation = field.getAnnotation(FixedArrayLength.class);
                return annotation != null ? Optional.of(annotation.value()) : Optional.empty();
            } catch (NoSuchFieldException ignored) {
                // keep looking up the hierarchy
            }
        }
        return Optional.empty();
    }
}
