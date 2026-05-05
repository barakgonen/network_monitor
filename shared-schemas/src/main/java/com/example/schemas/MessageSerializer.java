package com.example.schemas;

import java.util.Map;

public interface MessageSerializer {
    byte[] serialize(Map<String, Object> fields) throws Exception;

    String messageType();
}
