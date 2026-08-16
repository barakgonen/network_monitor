package com.example.schemas.ping;

import java.nio.ByteBuffer;

public record PingMessage(int sequence) {
    public static PingMessage fromByteBuffer(ByteBuffer buffer) {
        return new PingMessage(buffer.getInt());
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(sequence);
    }
}
