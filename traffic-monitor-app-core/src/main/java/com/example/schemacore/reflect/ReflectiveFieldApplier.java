package com.example.schemacore.reflect;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reflectively builds a message instance from a generic field map (the inverse of
 * {@link ReflectiveFieldExtractor}), used by the field-map based publish endpoints. Records are
 * built via their canonical constructor; plain classes via a no-arg constructor plus setters.
 * Nested/complex fields arrive as dotted paths (e.g. {@code "header.msgType"}, mirroring
 * {@link com.example.monitor.publisher.PublisherFieldMetadataService}'s flattening) - {@link
 * #unflatten} regroups one level of dotted keys per call, and {@link #coerce} recurses into
 * {@link #build} for nested targets, so arbitrarily deep nesting resolves one level per
 * recursive {@code build} call. Array-of-struct fields arrive as indexed paths (e.g.
 * {@code "trackData[0].id"}) - {@link #unflatten} groups those by index into a
 * {@code Map<Integer, Object>} instead of a plain nested map, and {@link #buildArray} resolves
 * the target array's length from {@link com.example.schemacore.annotation.FixedArrayLength}
 * (falling back to the highest index submitted) and pads any index the caller didn't submit with
 * a default-constructed element, the same way a fixed-array-length message class's own no-arg
 * constructor typically pre-populates every slot.
 */
public final class ReflectiveFieldApplier {
    private static final Pattern PATH_SEGMENT = Pattern.compile("^([^.\\[]+)(?:\\[(\\d+)])?(?:\\.(.+))?$");

    private ReflectiveFieldApplier() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T build(Class<T> type, Map<String, Object> fields) throws Exception {
        Map<String, Object> nested = unflatten(fields);
        if (type.isRecord()) {
            return buildRecord(type, nested);
        }
        return buildViaSetters(type, nested);
    }

    private static Map<String, Object> unflatten(Map<String, Object> flat) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : flat.entrySet()) {
            Matcher matcher = PATH_SEGMENT.matcher(entry.getKey());
            if (!matcher.matches()) {
                result.put(entry.getKey(), entry.getValue());
                continue;
            }

            String head = matcher.group(1);
            String index = matcher.group(2);
            String tail = matcher.group(3);

            if (index != null) {
                @SuppressWarnings("unchecked")
                Map<Integer, Object> indexed = (Map<Integer, Object>) result.computeIfAbsent(head, k -> new TreeMap<Integer, Object>());
                putIndexed(indexed, Integer.parseInt(index), tail, entry.getValue());
                continue;
            }

            if (tail == null) {
                result.put(head, entry.getValue());
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> child = (Map<String, Object>) result.computeIfAbsent(head, k -> new LinkedHashMap<String, Object>());
            child.put(tail, entry.getValue());
        }
        return result;
    }

    private static void putIndexed(Map<Integer, Object> indexed, int index, String tail, Object value) {
        if (tail == null) {
            indexed.put(index, value);
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) indexed.computeIfAbsent(index, k -> new LinkedHashMap<String, Object>());
        item.put(tail, value);
    }

    private static <T> T buildRecord(Class<T> type, Map<String, Object> fields) throws Exception {
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] paramTypes = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            paramTypes[i] = components[i].getType();
            args[i] = coerce(fields.get(components[i].getName()), paramTypes[i], type, components[i].getName());
        }

        Constructor<T> constructor = type.getDeclaredConstructor(paramTypes);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    private static <T> T buildViaSetters(Class<T> type, Map<String, Object> fields) throws Exception {
        Constructor<T> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        T instance = constructor.newInstance();

        for (Map.Entry<String, Object> entry : fields.entrySet()) {
            Method setter = findSetter(type, entry.getKey());
            if (setter != null) {
                setter.invoke(instance, coerce(entry.getValue(), setter.getParameterTypes()[0], type, entry.getKey()));
            }
        }

        return instance;
    }

    private static Method findSetter(Class<?> type, String fieldName) {
        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (Method method : type.getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method;
            }
        }
        return null;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object coerce(Object value, Class<?> targetType, Class<?> owner, String fieldName) throws Exception {
        if (value == null) {
            return PrimitiveWireTypes.defaultValue(targetType);
        }

        if (targetType.isInstance(value)) {
            return value;
        }

        if (targetType.isArray() && value instanceof Map<?, ?> indexedGroups) {
            return buildArray(indexedGroups, targetType.getComponentType(), owner, fieldName);
        }

        if (value instanceof Map<?, ?> nestedFields) {
            return build(targetType, (Map<String, Object>) nestedFields);
        }

        if (targetType.isEnum() && value instanceof String stringValue) {
            return enumFromWireValue((Class<Enum>) targetType, stringValue);
        }

        if (PrimitiveWireTypes.isNumericType(targetType) && value instanceof String stringValue) {
            return PrimitiveWireTypes.coerceNumber(Double.parseDouble(stringValue), targetType);
        }

        if (value instanceof Number number) {
            return PrimitiveWireTypes.coerceNumber(number, targetType);
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        }

        return value;
    }

    /**
     * Builds a fixed-length array from a sparse {@code index -> item fields} map. Length comes
     * from the owning field's {@link FixedArrayLengths} lookup when present (the wire format
     * demands exactly that many elements), else from the highest submitted index - either way, any index
     * the caller didn't submit is filled with a default-constructed element rather than left
     * null, since the UI lets rows be added/removed independently of array position.
     */
    @SuppressWarnings("unchecked")
    private static Object buildArray(Map<?, ?> indexedGroups, Class<?> componentType, Class<?> owner, String fieldName) throws Exception {
        int length = FixedArrayLengths.find(owner, fieldName)
                .orElseGet(() -> indexedGroups.keySet().stream().mapToInt(key -> (Integer) key).max().orElse(-1) + 1);

        Object array = Array.newInstance(componentType, length);
        for (int i = 0; i < length; i++) {
            Object itemFields = indexedGroups.get(i);
            Object element = itemFields != null
                    ? build(componentType, (Map<String, Object>) itemFields)
                    : componentType.getDeclaredConstructor().newInstance();
            Array.set(array, i, element);
        }
        return array;
    }

    /**
     * Mirrors {@link ReflectiveFieldExtractor}'s enum wire-value convention: if the enum exposes
     * {@code getWireName()}, match case-insensitively against that; otherwise fall back to the
     * Java constant name.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumFromWireValue(Class<? extends Enum> type, String value) {
        Method wireNameMethod;
        try {
            wireNameMethod = type.getMethod("getWireName");
        } catch (NoSuchMethodException e) {
            return Enum.valueOf(type, value);
        }

        try {
            for (Object constant : type.getEnumConstants()) {
                Object wireName = wireNameMethod.invoke(constant);
                if (value.equalsIgnoreCase(String.valueOf(wireName))) {
                    return constant;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed reading getWireName() on " + type.getName(), e);
        }

        return Enum.valueOf(type, value);
    }
}
