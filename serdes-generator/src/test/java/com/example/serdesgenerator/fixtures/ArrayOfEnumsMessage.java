package com.example.serdesgenerator.fixtures;

import com.example.schemacore.annotation.EnumWireSize;
import com.example.schemacore.annotation.FixedArrayLength;

import java.nio.ByteBuffer;

public class ArrayOfEnumsMessage {
    @FixedArrayLength(2)
    @EnumWireSize(1)
    private CodedEnum[] values = new CodedEnum[2];

    public ArrayOfEnumsMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
