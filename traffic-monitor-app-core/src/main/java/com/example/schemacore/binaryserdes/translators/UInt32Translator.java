package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * Unsigned 32-bit integer (0..4294967295)
 */
public class UInt32Translator implements Translator<Long> {

    @Override
    public Long fromBytes(ByteBuffer buffer) {
        return Integer.toUnsignedLong(buffer.getInt());
    }

    @Override
    public void toBytes(Long value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value < 0 || value > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("Value out of range for uint32: " + value);
        }
        buffer.putInt((int) (value & 0xFFFFFFFFL));
    }
}
