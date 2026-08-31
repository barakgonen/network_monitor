package com.example.schemacore.binaryserdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.nio.ByteBuffer;

/**
 * A fixed-length array of a single element {@link Type} (primitive or {@link RecordType}).
 * Represented as a JSON array at the protocol boundary. Mirrors {@link RecordType}'s
 * shape/translator-building approach, but for a homogeneous repeated element instead of named
 * fields.
 */
public final class ArrayType extends Type<ArrayNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Type<?> elementType;
    private final int length;

    public ArrayType(String name, Type<?> elementType, int length) {
        super(name, computeSize(elementType, length), ArrayNode.class, buildTranslator(elementType, length));
        this.elementType = elementType;
        this.length = length;
    }

    public Type<?> getElementType() {
        return elementType;
    }

    public int getLength() {
        return length;
    }

    private static int computeSize(Type<?> elementType, int length) {
        int elementSize = elementType.getSizeInBytes();
        if (elementSize < 0) {
            // A variable-length element (e.g. a string) makes the whole array variable-size too.
            return -1;
        }
        return elementSize * length;
    }

    private static Translator<ArrayNode> buildTranslator(Type<?> elementType, int length) {
        return new Translator<>() {
            @Override
            public ArrayNode fromBytes(ByteBuffer buffer) {
                ArrayNode array = MAPPER.createArrayNode();

                for (int i = 0; i < length; i++) {
                    Object value = elementType.getTranslator().fromBytes(buffer);
                    // A nested RecordType/ArrayType element already decodes to a JsonNode (e.g.
                    // ObjectNode) - add it directly rather than via addPOJO, which would wrap it
                    // in an opaque POJONode instead of embedding it as a proper tree node.
                    if (value instanceof JsonNode jsonNode) {
                        array.add(jsonNode);
                    } else {
                        array.addPOJO(value);
                    }
                }

                return array;
            }

            @Override
            public void toBytes(ArrayNode value, ByteBuffer buffer) {
                if (value == null || value.size() != length) {
                    throw new IllegalArgumentException(
                            "Expected an array of exactly " + length + " elements, got: "
                                    + (value == null ? "null" : value.size()));
                }

                @SuppressWarnings("unchecked")
                Translator<Object> translator = (Translator<Object>) elementType.getTranslator();
                Class<?> javaType = elementType.getJavaType();

                for (JsonNode elementNode : value) {
                    Object converted = javaType != null
                            ? MAPPER.convertValue(elementNode, javaType)
                            : MAPPER.convertValue(elementNode, Object.class);
                    translator.toBytes(converted, buffer);
                }
            }
        };
    }
}
