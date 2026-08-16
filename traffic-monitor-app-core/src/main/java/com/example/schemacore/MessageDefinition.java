package com.example.schemacore;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Describes one wire message type: its opcode, the interface/message-type it belongs to, and how
 * to move between raw bytes, a typed message object, and a generic field map. Most
 * implementations are {@code com.example.schemacore.reflect.ReflectiveMessageDefinition}
 * instances built from YAML config rather than hand-written classes. The message type itself is
 * a plain {@code Class<?>} - no marker interface required - so classes owned by an external
 * dependency can be wired in as-is.
 */
public interface MessageDefinition {
    String interfaceName();

    String messageType();

    int opcode();

    Class<?> messageClass();

    Map<String, Object> decodeBody(ByteBuffer body) throws Exception;

    Object decodeMessage(ByteBuffer body) throws Exception;

    byte[] encodeBody(Map<String, Object> fields) throws Exception;

    byte[] encodeBody(Object message) throws Exception;
}
