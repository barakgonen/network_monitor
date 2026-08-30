package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * Unsigned 16-bit integer (0..65535)
 */
public class UInt16Translator implements Translator<Integer> {

    @Override
    public Integer fromBytes(ByteBuffer buffer) {
        return Short.toUnsignedInt(buffer.getShort());
    }

    @Override
    public void toBytes(Integer value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range for uint16: " + value);
        }
        buffer.putShort((short) (value & 0xFFFF));
    }
}
