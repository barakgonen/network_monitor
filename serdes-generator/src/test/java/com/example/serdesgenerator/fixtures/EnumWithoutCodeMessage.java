package com.example.serdesgenerator.fixtures;

import com.example.schemacore.annotation.EnumWireSize;

import java.nio.ByteBuffer;

public class EnumWithoutCodeMessage {
    @EnumWireSize(1)
    private PlainEnum value;

    public EnumWithoutCodeMessage(byte[] payload) {
    }

    public void toByteArray(ByteBuffer buffer) {
    }
}
