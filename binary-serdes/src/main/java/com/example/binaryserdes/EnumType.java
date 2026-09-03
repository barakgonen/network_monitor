package com.example.binaryserdes;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A named enum backed by a numeric wire type (e.g. {@code uint8}): decodes to/from its symbolic
 * name (a JSON string), the same way {@link RecordType}/{@link ArrayType} decode to a JSON
 * object/array rather than raw bytes, instead of exposing the underlying numeric code directly.
 */
public final class EnumType extends Type<String> {

    private final Type<?> underlyingType;
    private final Map<String, Integer> valuesByName;

    public EnumType(String name, Type<?> underlyingType, Map<String, Integer> valuesByName) {
        super(name, underlyingType.getSizeInBytes(), String.class,
                buildTranslator(name, underlyingType, valuesByName));
        this.underlyingType = underlyingType;
        this.valuesByName = Collections.unmodifiableMap(new LinkedHashMap<>(valuesByName));
    }

    public Type<?> getUnderlyingType() {
        return underlyingType;
    }

    public Map<String, Integer> getValuesByName() {
        return valuesByName;
    }

    private static Translator<String> buildTranslator(
            String enumName, Type<?> underlyingType, Map<String, Integer> valuesByName) {
        Map<String, Integer> byName = new LinkedHashMap<>(valuesByName);
        Map<Integer, String> byCode = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : byName.entrySet()) {
            byCode.put(e.getValue(), e.getKey());
        }

        @SuppressWarnings("unchecked")
        Translator<Object> numericTranslator = (Translator<Object>) underlyingType.getTranslator();
        Class<?> underlyingJavaType = underlyingType.getJavaType();

        return new Translator<>() {
            @Override
            public String fromBytes(ByteBuffer buffer) {
                Object raw = numericTranslator.fromBytes(buffer);
                int code = ((Number) raw).intValue();
                String name = byCode.get(code);
                if (name == null) {
                    throw new IllegalStateException(
                            "Unknown wire value " + code + " for enum '" + enumName + "' - known values: "
                                    + byCode);
                }
                return name;
            }

            @Override
            public void toBytes(String value, ByteBuffer buffer) {
                if (value == null) {
                    throw new IllegalArgumentException("value must not be null");
                }
                Integer code = byName.get(value);
                if (code == null) {
                    throw new IllegalArgumentException(
                            "Unknown value '" + value + "' for enum '" + enumName + "' - expected one of "
                                    + byName.keySet());
                }
                Object numeric = underlyingJavaType == Long.class ? Long.valueOf(code) : (Object) code;
                numericTranslator.toBytes(numeric, buffer);
            }
        };
    }
}
