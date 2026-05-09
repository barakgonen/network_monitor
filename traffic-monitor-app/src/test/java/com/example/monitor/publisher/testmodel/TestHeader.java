package com.example.monitor.publisher.testmodel;

import java.nio.ByteBuffer;

public class TestHeader {
    public int opcode;
    public short sequence;

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(opcode);
        buffer.putShort(sequence);
    }
}
