package com.example.schemas.greeting;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record GreetingMessage(int id, String text) {
    public static GreetingMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Integer.BYTES) {
            throw new IllegalArgumentException("Greeting body is too short");
        }

        int id = buffer.getInt();
        int textLength = buffer.getInt();

        if (textLength < 0 || textLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid text length: " + textLength);
        }

        byte[] textBytes = new byte[textLength];
        buffer.get(textBytes);

        return new GreetingMessage(id, new String(textBytes, StandardCharsets.UTF_8));
    }

    public byte[] toByteArray() {
        byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + Integer.BYTES + textBytes.length);
        buffer.putInt(id);
        buffer.putInt(textBytes.length);
        buffer.put(textBytes);

        return buffer.array();
    }
}
