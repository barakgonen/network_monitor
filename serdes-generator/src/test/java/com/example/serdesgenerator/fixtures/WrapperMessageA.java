package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

/** Root fixture referencing NestedStruct directly (not via array) - for recursion + dedup tests. */
public class WrapperMessageA {
    private NestedStruct nested;

    public WrapperMessageA(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
