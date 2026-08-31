package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * 64-bit integer (Java long). Also used for wire fields the source protocol calls "unsigned
 * 64-bit" - Java's {@code long} already covers the full unsigned 64-bit bit pattern, so no
 * separate uint64 translator/range-checking is needed the way there is for uint8/16/32.
 */
public class Int64Translator implements Translator<Long> {

    @Override
    public Long fromBytes(ByteBuffer buffer) {
        return buffer.getLong();
    }

    @Override
    public void toBytes(Long value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        buffer.putLong(value);
    }
}
