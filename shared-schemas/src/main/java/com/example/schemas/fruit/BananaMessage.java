package com.example.schemas.fruit;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record BananaMessage(
        String color,
        double weight
) implements ProtocolMessage {

    public static BananaMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Double.BYTES) {
            throw new IllegalArgumentException("Banana body is too short");
        }

        int colorLength = buffer.getInt();

        if (colorLength < 0 || colorLength > buffer.remaining() - Double.BYTES) {
            throw new IllegalArgumentException("Invalid color length: " + colorLength);
        }

        byte[] colorBytes = new byte[colorLength];
        buffer.get(colorBytes);

        double weight = buffer.getDouble();

        return new BananaMessage(new String(colorBytes, StandardCharsets.UTF_8), weight);
    }

    public byte[] toByteArray() {
        byte[] colorBytes = color.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + colorBytes.length + Double.BYTES);
        buffer.putInt(colorBytes.length);
        buffer.put(colorBytes);
        buffer.putDouble(weight);

        return buffer.array();
    }
}
