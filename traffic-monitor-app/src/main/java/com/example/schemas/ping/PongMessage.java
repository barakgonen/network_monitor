package com.example.schemas.ping;

import java.nio.ByteBuffer;

public record PongMessage(int sequence) {
    public static PongMessage fromByteBuffer(ByteBuffer buffer) {
        return new PongMessage(buffer.getInt());
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(sequence);
    }
}
