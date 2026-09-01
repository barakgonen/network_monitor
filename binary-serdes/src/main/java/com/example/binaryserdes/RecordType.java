package com.example.binaryserdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A "record" type: a composite value made of named fields, each with its own Type.
 * Represented as a JSON object at the protocol boundary.
 */
public final class RecordType extends Type<ObjectNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ordered, so binary layout is deterministic
    private final Map<String, Type<?>> fields;

    public RecordType(String name, Map<String, Type<?>> fields) {
        super(
                name,
                computeSize(fields),              // fixed size or -1 if any field is variable
                ObjectNode.class,
                buildTranslator(fields)           // custom translator for record
        );
        // keep our own copy, preserve insertion order
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }

    public Map<String, Type<?>> getFields() {
        return fields;
    }

    /**
     * Convenience factory for small records.
     */
    @SafeVarargs
    public static RecordType of(String name, Map.Entry<String, Type<?>>... entries) {
        Map<String, Type<?>> map = new LinkedHashMap<>();
        for (Map.Entry<String, Type<?>> e : entries) {
            map.put(e.getKey(), e.getValue());
        }
        return new RecordType(name, map);
    }

    /* ----------- size calculation ----------- */

    private static int computeSize(Map<String, Type<?>> fields) {
        int total = 0;
        for (Type<?> t : fields.values()) {
            int sz = t.getSizeInBytes();
            if (sz < 0) {
                // variable-size field -> record is variable-size
                return -1;
            }
            total += sz;
        }
        return total;
    }

    /* ----------- translator for the whole record ----------- */

    private static Translator<ObjectNode> buildTranslator(Map<String, Type<?>> fields) {
        // important: capture a *snapshot* of fields
        Map<String, Type<?>> fieldSnapshot = new LinkedHashMap<>(fields);

        return new Translator<>() {
            @Override
            public ObjectNode fromBytes(ByteBuffer buffer) {
                ObjectNode node = MAPPER.createObjectNode();

                for (Map.Entry<String, Type<?>> e : fieldSnapshot.entrySet()) {
                    String fieldName = e.getKey();
                    Type<?> fieldType = e.getValue();

                    Object value = fieldType.getTranslator().fromBytes(buffer);
                    node.putPOJO(fieldName, value);
                }

                return node;
            }

            @Override
            public void toBytes(ObjectNode value, ByteBuffer buffer) {
                for (Map.Entry<String, Type<?>> e : fieldSnapshot.entrySet()) {
                    String fieldName = e.getKey();
                    Type<?> fieldType = e.getValue();

                    JsonNode fieldNode = value.get(fieldName);
                    if (fieldNode == null || fieldNode.isNull()) {
                        throw new IllegalArgumentException(
                                "Missing or null field '" + fieldName +
                                        "' for record type '" + fieldSnapshot + "'"
                        );
                    }

                    Class<?> javaType = fieldType.getJavaType();

                    Object converted;
                    if (javaType != null) {
                        converted = MAPPER.convertValue(fieldNode, javaType);
                    } else {
                        converted = MAPPER.convertValue(fieldNode, Object.class);
                    }

                    @SuppressWarnings("unchecked")
                    Translator<Object> translator =
                            (Translator<Object>) fieldType.getTranslator();

                    translator.toBytes(converted, buffer);
                }
            }
        };
    }
}
