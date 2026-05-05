package com.example.schemas;

public interface MessageParser {
    ParsedMessage parse(byte[] payload) throws Exception;

    String messageType();
}
