package com.example.serdesgenerator.fixtures;

import java.nio.ByteBuffer;

/** Second root fixture also referencing NestedStruct - for the type-dedup test. */
public class WrapperMessageB {
    private NestedStruct nested;

    public WrapperMessageB(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
