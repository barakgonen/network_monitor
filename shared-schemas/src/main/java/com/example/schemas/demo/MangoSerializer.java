package com.example.schemas.demo;

import com.example.schemas.MessageSerializer;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MangoSerializer implements MessageSerializer {
    @Override
    public byte[] serialize(Map<String, Object> fields) {
        return fields.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String messageType() {
        return "Mango";
    }
}
