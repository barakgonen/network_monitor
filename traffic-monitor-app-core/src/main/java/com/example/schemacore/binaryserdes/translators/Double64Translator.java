package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;

public class Double64Translator implements Translator<Double> {
    @Override
    public Double fromBytes(ByteBuffer buffer) {
        return buffer.getDouble();
    }

    @Override
    public void toBytes(Double value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        buffer.putDouble(value);
    }
}
