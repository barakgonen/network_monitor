package com.example.handlercore;

public interface ReplySender {
    void reply(Object message, String host, int port, String transport);
}
