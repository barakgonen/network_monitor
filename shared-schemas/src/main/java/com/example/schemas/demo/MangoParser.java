package com.example.schemas.demo;

import com.example.schemas.MessageParser;
import com.example.schemas.ParsedMessage;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MangoParser implements MessageParser {
    @Override
    public ParsedMessage parse(byte[] payload) {
        return new ParsedMessage("Mango", Map.of("text", new String(payload, StandardCharsets.UTF_8)));
    }

    @Override
    public String messageType() {
        return "Mango";
    }
}
