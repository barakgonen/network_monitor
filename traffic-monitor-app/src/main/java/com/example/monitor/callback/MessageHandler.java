package com.example.monitor.callback;

@FunctionalInterface
public interface MessageHandler<T> {

    void handle(T message);
}
