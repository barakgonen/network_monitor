package com.example.monitor.publisher.testmodel;

import java.nio.ByteBuffer;

public class DirectByteArrayMessage {
    public TestHeader header = new TestHeader();
    public double latitude;
    public TestStatus status = TestStatus.IDLE;
    public TestPlot[] plots = new TestPlot[3];

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(4 + 2 + 8 + 1 + (3 * 8));
        header.toByteArray(buffer);
        buffer.putDouble(latitude);
        buffer.put((byte) status.ordinal());
        for (TestPlot plot : plots) {
            if (plot == null) {
                plot = new TestPlot();
            }
            plot.toByteArray(buffer);
        }
        return buffer.array();
    }
}
