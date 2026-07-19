package com.example.schemacore;

/**
 * Marker interface implemented by every concrete protocol message class, so generic engine code
 * (registries, codecs, publisher) can traffic in {@code ProtocolMessage} without depending on any
 * specific protocol.
 */
public interface ProtocolMessage {
}
