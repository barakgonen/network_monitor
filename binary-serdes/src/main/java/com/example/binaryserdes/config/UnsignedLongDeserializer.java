package com.example.binaryserdes.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * Parses an opcode literal as a raw 64-bit {@code long} bit pattern. A plain JSON number (or a
 * quoted string within the signed range) parses via {@link Long#parseLong}; a quoted string in
 * the unsigned-only upper half of the 64-bit range (&gt;= 2^63, which doesn't fit as a positive
 * signed long literal) falls back to {@link Long#parseUnsignedLong}. Values that fit a plain
 * signed long keep working exactly as before this deserializer existed.
 */
public final class UnsignedLongDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        JsonNode node = p.getCodec().readTree(p);
        if (node.isTextual()) {
            String text = node.textValue();
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return Long.parseUnsignedLong(text);
            }
        }
        return node.longValue();
    }
}
