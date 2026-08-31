package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * 32-bit IEEE 754 float.
 */
public class Float32Translator implements Translator<Float> {

    @Override
    public Float fromBytes(ByteBuffer buffer) {
        return buffer.getFloat();
    }

    @Override
    public void toBytes(Float value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        buffer.putFloat(value);
    }
}
