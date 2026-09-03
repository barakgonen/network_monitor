package com.example.serdesgenerator.fixtures;

import com.example.schemacore.annotation.FixedArrayLength;

import java.nio.ByteBuffer;

public class ArrayOfStructsMessage {
    @FixedArrayLength(3)
    private NestedStruct[] items = new NestedStruct[3];

    public ArrayOfStructsMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
