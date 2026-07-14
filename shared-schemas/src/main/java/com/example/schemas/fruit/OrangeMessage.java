package com.example.schemas.fruit;

import com.example.schemacore.ProtocolMessage;

public record OrangeMessage(
        String sourceFarm,
        FruitFreshness freshness
) implements ProtocolMessage {
}
