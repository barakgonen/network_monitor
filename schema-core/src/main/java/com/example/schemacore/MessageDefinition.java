package com.example.schemacore;

import java.nio.ByteBuffer;
import java.util.Map;

public interface MessageDefinition {
    String interfaceName();

    String messageType();

    int opcode();

    Map<String, Object> decodeBody(ByteBuffer body) throws Exception;

    byte[] encodeBody(Map<String, Object> fields) throws Exception;
}
