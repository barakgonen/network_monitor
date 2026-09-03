package com.example.binaryserdes.translators;

import com.example.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * A single raw, signed 8-bit value (Java's {@code byte}, -128..127) - distinct from
 * {@link UInt8Translator}'s unsigned 0..255 {@code Integer} interpretation of the same one wire
 * byte.
 */
public class ByteTranslator implements Translator<Byte> {

    @Override
    public Byte fromBytes(ByteBuffer buffer) {
        return buffer.get();
    }

    @Override
    public void toBytes(Byte value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        buffer.put(value);
    }
}
