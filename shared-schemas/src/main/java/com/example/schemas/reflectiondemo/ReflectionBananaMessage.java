package com.example.schemas.reflectiondemo;

import com.example.schemas.fruit.FruitProtocolCodec;

import java.nio.ByteBuffer;

public class ReflectionBananaMessage {
    private final String color;
    private final double weight;

    public ReflectionBananaMessage(byte[] payload) {
        this(ByteBuffer.wrap(payload));
    }

    public ReflectionBananaMessage(ByteBuffer buffer) {
        int start = buffer.position();
        byte[] remaining = new byte[buffer.remaining()];
        buffer.get(remaining);

        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(remaining);

        if (!"Banana".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not Banana. actual=" + decoded.messageType());
        }

        this.color = String.valueOf(decoded.bodyFields().get("color"));
        this.weight = ((Number) decoded.bodyFields().get("weight")).doubleValue();

        buffer.position(start + remaining.length);
    }

    public String getColor() {
        return color;
    }

    public double getWeight() {
        return weight;
    }
}
