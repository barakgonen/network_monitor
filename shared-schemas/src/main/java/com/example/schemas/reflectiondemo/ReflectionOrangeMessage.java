package com.example.schemas.reflectiondemo;

import com.example.schemas.fruit.FruitProtocolCodec;

import java.nio.ByteBuffer;

public class ReflectionOrangeMessage {
    private final String sourceFarm;
    private final String freshness;

    public ReflectionOrangeMessage(byte[] payload) {
        this(ByteBuffer.wrap(payload));
    }

    public ReflectionOrangeMessage(ByteBuffer buffer) {
        int start = buffer.position();
        byte[] remaining = new byte[buffer.remaining()];
        buffer.get(remaining);

        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(remaining);

        if (!"Orange".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not Orange. actual=" + decoded.messageType());
        }

        this.sourceFarm = String.valueOf(decoded.bodyFields().get("sourceFarm"));
        this.freshness = String.valueOf(decoded.bodyFields().get("freshness"));

        buffer.position(start + remaining.length);
    }

    public String getSourceFarm() {
        return sourceFarm;
    }

    public String getFreshness() {
        return freshness;
    }
}
