package com.example.monitor.publisher.testmodel;

import java.nio.ByteBuffer;

public class TestPlot {
    public float x;
    public float y;

    public void toByteArray(ByteBuffer buffer) {
        buffer.putFloat(x);
        buffer.putFloat(y);
    }
}
