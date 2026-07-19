package com.example.schemas.ping;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;

public record PingMessage(int sequence) implements ProtocolMessage {
    public static PingMessage fromByteBuffer(ByteBuffer buffer) {
        return new PingMessage(buffer.getInt());
    }

    public void toByteArray(ByteBuffer buffer) {
        buffer.putInt(sequence);
    }
}
