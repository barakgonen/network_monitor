package com.example.schemas.reflectiondemo;

import com.example.schemas.fruit.FruitProtocolCodec;

public class ReflectionBananaMessage {
    private final String color;
    private final double weight;

    public ReflectionBananaMessage(byte[] payload) {
        FruitProtocolCodec.DecodedFruitMessage decoded = new FruitProtocolCodec().decode(payload);

        if (!"Banana".equals(decoded.messageType())) {
            throw new IllegalArgumentException("Payload is not Banana. actual=" + decoded.messageType());
        }

        this.color = String.valueOf(decoded.bodyFields().get("color"));
        this.weight = ((Number) decoded.bodyFields().get("weight")).doubleValue();
    }

    public String getColor() {
        return color;
    }

    public double getWeight() {
        return weight;
    }
}
