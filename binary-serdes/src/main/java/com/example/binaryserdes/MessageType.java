package com.example.binaryserdes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schema of a message: name + opcode + ordered fields.
 * <p>
 * Builder usage:
 * <p>
 * MessageType mt = MessageType.builder()
 * .name("HeaderMessage")
 * .opcode(1)
 * .field(new MessageField<>("msgId", u16))
 * .field(new MessageField<>("timestamp", u32))
 * .build();
 */
public final class MessageType {

    private final String name;
    private final int opcode;
    private final List<MessageField<?>> fields;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private MessageType(String name, int opcode, List<MessageField<?>> fields) {
        this.name = name;
        this.opcode = opcode;
        this.fields = Collections.unmodifiableList(fields);
    }

    public String getName() {
        return name;
    }

    public int getOpcode() {
        return opcode;
    }

    public List<MessageField<?>> getFields() {
        return fields;
    }

    public static MessageTypeBuilder builder() {
        return new MessageTypeBuilder();
    }

    /* ---------- Extraction: bytes -> JSON ---------- */

    public String parseToJson(byte[] bytes) {
        return parseToJson(bytes, ByteOrder.BIG_ENDIAN);
    }

    public String parseToJson(byte[] bytes, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(byteOrder);
        ObjectNode node = OBJECT_MAPPER.createObjectNode();

        for (MessageField<?> field : fields) {
            Type<?> type = field.getType();
            Object value = type.getTranslator().fromBytes(buffer);
            node.putPOJO(field.getFieldName(), value);
        }

        return node.toString();
    }

    /* ---------- Injection: JSON -> bytes / ByteBuffer ---------- */

    public void writeToBuffer(ObjectNode jsonNode, ByteBuffer buffer) {
        for (MessageField<?> field : fields) {
            String fieldName = field.getFieldName();
            JsonNode fieldNode = jsonNode.get(fieldName);
            if (fieldNode == null || fieldNode.isNull()) {
                throw new IllegalArgumentException(
                        "Missing or null field '" + fieldName + "' for message '" + name + "'");
            }

            Type<?> type = field.getType();
            Object value;

            if (type.getJavaType() != null) {
                value = OBJECT_MAPPER.convertValue(fieldNode, type.getJavaType());
            } else {
                value = OBJECT_MAPPER.convertValue(fieldNode, Object.class);
            }

            @SuppressWarnings("unchecked")
            Translator<Object> translator = (Translator<Object>) type.getTranslator();
            translator.toBytes(value, buffer);
        }
    }

    public void writeToBuffer(String json, ByteBuffer buffer) throws IOException {
        JsonNode node = OBJECT_MAPPER.readTree(json);
        if (!node.isObject()) {
            throw new IllegalArgumentException("JSON for message '" + name + "' must be an object");
        }
        writeToBuffer((ObjectNode) node, buffer);
    }

    public byte[] toBytes(String json) throws IOException {
        return toBytes(json, ByteOrder.BIG_ENDIAN);
    }

    public byte[] toBytes(String json, ByteOrder byteOrder) throws IOException {
        int totalSize = 0;
        for (MessageField<?> field : fields) {
            int size = field.getType().getSizeInBytes();
            if (size < 0) {
                throw new IllegalStateException(
                        "Cannot auto-allocate buffer for message '" + name +
                                "' because field '" + field.getFieldName() + "' is variable-length");
            }
            totalSize += size;
        }

        ByteBuffer buffer = ByteBuffer.allocate(totalSize).order(byteOrder);
        writeToBuffer(json, buffer);
        return buffer.array();
    }

    public static final class MessageTypeBuilder {
        private String name;
        private int opcode;
        private final List<MessageField<?>> fields = new ArrayList<>();

        private MessageTypeBuilder() {
        }

        public MessageTypeBuilder name(String name) {
            this.name = name;
            return this;
        }

        public MessageTypeBuilder opcode(int opcode) {
            this.opcode = opcode;
            return this;
        }

        public MessageTypeBuilder field(MessageField<?> field) {
            this.fields.add(field);
            return this;
        }

        /** Lombok's {@code @Singular} generates this alongside the singular adder above: appends every element rather than replacing the list. */
        public MessageTypeBuilder fields(List<MessageField<?>> fields) {
            this.fields.addAll(fields);
            return this;
        }

        public MessageType build() {
            return new MessageType(name, opcode, fields);
        }
    }
}
