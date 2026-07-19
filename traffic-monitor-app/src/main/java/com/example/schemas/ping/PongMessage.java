package com.example.schemas.ping;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;

public record PongMessage(int sequence) implements ProtocolMessage {
    public static PongMessage fromByteBuffer(ByteBuffer buffer) {
        return new PongMessage(buffer.getInt());
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(sequence);
    }
}
