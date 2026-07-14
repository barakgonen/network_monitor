package com.example.schemas.fruit;

import com.example.schemacore.ProtocolMessage;

public record BananaMessage(
        String color,
        double weight
) implements ProtocolMessage {
}
