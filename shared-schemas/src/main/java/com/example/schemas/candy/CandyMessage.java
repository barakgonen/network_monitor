package com.example.schemas.candy;

import com.example.schemacore.ProtocolMessage;

public record CandyMessage(String name, double calories) implements ProtocolMessage {
}
