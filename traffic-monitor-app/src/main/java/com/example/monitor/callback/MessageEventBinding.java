package com.example.monitor.callback;

public interface MessageEventBinding<T> {

    Class<T> messageType();

    EventKey eventKey();

    MessageHandler<? super T> handler();
}
