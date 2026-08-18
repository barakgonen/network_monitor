package com.example.handlercore;

public interface ReplySender {
    void reply(byte[] payload, String host, int port, String transport);
}
