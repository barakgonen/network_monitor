package com.example.schemas.ping;

import com.example.schemacore.ProtocolMessage;

public record PongMessage(int sequence) implements ProtocolMessage {
}
