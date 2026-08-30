package com.example.tester.schemas.fruit;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public record OrangeMessage(
        String sourceFarm,
        FruitFreshness freshness
) {

    public static OrangeMessage fromByteBuffer(ByteBuffer buffer) {
        if (buffer.remaining() < Integer.BYTES + Integer.BYTES) {
            throw new IllegalArgumentException("Orange body is too short");
        }

        int sourceFarmLength = buffer.getInt();

        if (sourceFarmLength < 0 || sourceFarmLength > buffer.remaining() - Integer.BYTES) {
            throw new IllegalArgumentException("Invalid sourceFarm length: " + sourceFarmLength);
        }

        byte[] sourceFarmBytes = new byte[sourceFarmLength];
        buffer.get(sourceFarmBytes);

        int freshnessLength = buffer.getInt();
        if (freshnessLength < 0 || freshnessLength > buffer.remaining()) {
            throw new IllegalArgumentException("Invalid freshness length: " + freshnessLength);
        }
        byte[] freshnessBytes = new byte[freshnessLength];
        buffer.get(freshnessBytes);
        FruitFreshness freshness = FruitFreshness.fromWireName(new String(freshnessBytes, StandardCharsets.UTF_8));

        return new OrangeMessage(new String(sourceFarmBytes, StandardCharsets.UTF_8), freshness);
    }

    public byte[] toByteArray() {
        byte[] sourceFarmBytes = sourceFarm.getBytes(StandardCharsets.UTF_8);
        byte[] freshnessBytes = freshness.getWireName().getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(
                Integer.BYTES + sourceFarmBytes.length + Integer.BYTES + freshnessBytes.length);
        buffer.putInt(sourceFarmBytes.length);
        buffer.put(sourceFarmBytes);
        buffer.putInt(freshnessBytes.length);
        buffer.put(freshnessBytes);

        return buffer.array();
    }
}
