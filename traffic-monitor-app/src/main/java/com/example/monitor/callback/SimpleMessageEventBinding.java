package com.example.monitor.callback;

public class SimpleMessageEventBinding<T> implements MessageEventBinding<T> {

    private final Class<T> messageType;
    private final EventKey eventKey;
    private final MessageHandler<? super T> handler;

    public SimpleMessageEventBinding(
            Class<T> messageType,
            EventKey eventKey,
            MessageHandler<? super T> handler
    ) {
        this.messageType = messageType;
        this.eventKey = eventKey;
        this.handler = handler;
    }

    @Override
    public Class<T> messageType() {
        return messageType;
    }

    @Override
    public EventKey eventKey() {
        return eventKey;
    }

    @Override
    public MessageHandler<? super T> handler() {
        return handler;
    }
}
