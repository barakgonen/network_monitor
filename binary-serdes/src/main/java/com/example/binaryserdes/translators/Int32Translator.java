package com.example.binaryserdes.translators;

import com.example.binaryserdes.Translator;

import java.nio.ByteBuffer;

/**
 * 32-bit signed integer (Java int) translator.
 */
public class Int32Translator implements Translator<Integer> {

    @Override
    public Integer fromBytes(ByteBuffer buffer) {
        return buffer.getInt();
    }

    @Override
    public void toBytes(Integer value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        buffer.putInt(value);
    }
}
