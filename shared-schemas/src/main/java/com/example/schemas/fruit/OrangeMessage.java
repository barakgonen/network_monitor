package com.example.schemas.fruit;

public record OrangeMessage(
        String sourceFarm,
        FruitFreshness freshness
) {
}
