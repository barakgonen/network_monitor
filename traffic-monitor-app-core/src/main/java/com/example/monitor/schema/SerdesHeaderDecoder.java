package com.example.monitor.schema;

import com.example.binaryserdes.RecordType;
import com.example.schemacore.HeaderDecoder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * Decodes a {@code messageOwnsHeader} interface's header via a JSON-schema-driven {@link
 * RecordType} (declared as a {@code record}-kind entry in the interface's {@code serdesFile:}
 * {@code types:} list) instead of a Java header class - for headers with no backing Java class,
 * e.g. rada's {@code RadaHeader} once it moved out of this module.
 */
public final class SerdesHeaderDecoder implements HeaderDecoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> FIELD_MAP_TYPE = new TypeReference<>() {
    };

    private final RecordType headerType;

    public SerdesHeaderDecoder(RecordType headerType) {
        this.headerType = headerType;
    }

    @Override
    public int headerSize() {
        return headerType.getSizeInBytes();
    }

    @Override
    public Map<String, Object> decode(byte[] headerBytes, ByteOrder byteOrder) {
        ByteBuffer buffer = ByteBuffer.wrap(headerBytes).order(byteOrder);
        ObjectNode node = headerType.getTranslator().fromBytes(buffer);
        return MAPPER.convertValue(node, FIELD_MAP_TYPE);
    }
}
