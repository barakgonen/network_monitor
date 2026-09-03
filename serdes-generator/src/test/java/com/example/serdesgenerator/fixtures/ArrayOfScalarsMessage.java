package com.example.serdesgenerator.fixtures;

import com.example.schemacore.annotation.FixedArrayLength;

import java.nio.ByteBuffer;

public class ArrayOfScalarsMessage {
    @FixedArrayLength(5)
    private byte[] values = new byte[5];

    public ArrayOfScalarsMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
