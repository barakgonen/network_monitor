package com.example.schemas.candy;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record CandyMessage(String name, double calories) implements ProtocolMessage {
    public static CandyMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES) {
            throw new IllegalArgumentException("Candy body is too short");
        }

        int nameLength = buffer.getInt();

        if (nameLength < 0 || nameLength > buffer.remaining() - Double.BYTES) {
            throw new IllegalArgumentException("Invalid name length: " + nameLength);
        }

        byte[] nameBytes = new byte[nameLength];
        buffer.get(nameBytes);

        double calories = buffer.getDouble();

        return new CandyMessage(new String(nameBytes, StandardCharsets.UTF_8), calories);
    }

    public byte[] toByteArray() {
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + nameBytes.length + Double.BYTES);
        buffer.putInt(nameBytes.length);
        buffer.put(nameBytes);
        buffer.putDouble(calories);

        return buffer.array();
    }
}
