package com.example.schemas.ping;

import com.example.schemacore.ProtocolMessage;

public record PingMessage(int sequence) implements ProtocolMessage {
}
