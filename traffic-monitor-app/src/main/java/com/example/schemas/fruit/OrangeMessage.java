package com.example.schemas.fruit;

import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record OrangeMessage(
        String sourceFarm,
        FruitFreshness freshness
) implements ProtocolMessage {

    public static OrangeMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Byte.BYTES) {
            throw new IllegalArgumentException("Orange body is too short");
        }

        int sourceFarmLength = buffer.getInt();

        if (sourceFarmLength < 0 || sourceFarmLength > buffer.remaining() - Byte.BYTES) {
            throw new IllegalArgumentException("Invalid sourceFarm length: " + sourceFarmLength);
        }

        byte[] sourceFarmBytes = new byte[sourceFarmLength];
        buffer.get(sourceFarmBytes);

        FruitFreshness freshness = FruitFreshness.fromCode(buffer.get());

        return new OrangeMessage(new String(sourceFarmBytes, StandardCharsets.UTF_8), freshness);
    }

    public byte[] toByteArray() {
        byte[] sourceFarmBytes = sourceFarm.getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES + sourceFarmBytes.length + Byte.BYTES);
        buffer.putInt(sourceFarmBytes.length);
        buffer.put(sourceFarmBytes);
        buffer.put(freshness.getCode());

        return buffer.array();
    }
}
