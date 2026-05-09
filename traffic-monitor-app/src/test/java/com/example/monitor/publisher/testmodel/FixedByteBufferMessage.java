package com.example.monitor.publisher.testmodel;

import java.nio.ByteBuffer;

public class FixedByteBufferMessage {
    public int first;
    public short second;
    public double third;

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(first);
        buffer.putShort(second);
        buffer.putDouble(third);
    }
}
