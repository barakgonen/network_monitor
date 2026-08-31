package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * Unsigned 8-bit integer (0..255)
 */
public class UInt8Translator implements Translator<Integer> {

    @Override
    public Integer fromBytes(ByteBuffer buffer) {
        return Byte.toUnsignedInt(buffer.get());
    }

    @Override
    public void toBytes(Integer value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of range for uint8: " + value);
        }
        buffer.put((byte) (value & 0xFF));
    }
}
