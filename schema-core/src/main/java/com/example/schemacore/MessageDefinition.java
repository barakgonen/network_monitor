package com.example.schemacore;

import java.nio.ByteBuffer;
import java.util.Map;

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
