package com.example.schemas.reflectiondemo;

import com.example.schemas.fruit.FruitProtocolCodec;

public class ReflectionOrangeMessage {
    private final String sourceFarm;
    private final String freshness;

    public ReflectionOrangeMessage(byte[] payload) {
        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(payload);

        if (!"Orange".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not Orange. actual=" + decoded.messageType());
        }

        this.sourceFarm = String.valueOf(decoded.bodyFields().get("sourceFarm"));
        this.freshness = String.valueOf(decoded.bodyFields().get("freshness"));
    }

    public String getSourceFarm() {
        return sourceFarm;
    }

    public String getFreshness() {
        return freshness;
    }
}
