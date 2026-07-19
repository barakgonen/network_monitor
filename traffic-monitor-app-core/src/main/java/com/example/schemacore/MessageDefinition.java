package com.example.schemacore;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Describes one wire message type: its opcode, the interface/message-type it belongs to, and how
 * to move between raw bytes, a typed {@link ProtocolMessage}, and a generic field map. Most
 * implementations are {@code com.example.schemacore.reflect.ReflectiveMessageDefinition}
 * instances built from YAML config rather than hand-written classes.
 */
public interface MessageDefinition {
    String interfaceName();

    String messageType();

    int opcode();

    Class<? extends ProtocolMessage> messageClass();

    Map<String, Object> decodeBody(ByteBuffer body) throws Exception;

    ProtocolMessage decodeMessage(ByteBuffer body) throws Exception;

    byte[] encodeBody(Map<String, Object> fields) throws Exception;

    byte[] encodeBody(ProtocolMessage message) throws Exception;
}
