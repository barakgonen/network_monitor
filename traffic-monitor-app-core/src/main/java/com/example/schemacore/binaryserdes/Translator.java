package com.example.schemacore.binaryserdes;

import java.nio.ByteBuffer;

/**
 * Converts between bytes and a Java value of type T.
 */
public interface Translator<T> {

    /**
     * Read a value of type T from the buffer, advancing its position.
     */
    T fromBytes(ByteBuffer buffer);

    /**
     * Write the given value into the buffer, advancing its position.
     */
    void toBytes(T value, ByteBuffer buffer);
}
