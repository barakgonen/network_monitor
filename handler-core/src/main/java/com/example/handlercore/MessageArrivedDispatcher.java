package com.example.handlercore;

public final class MessageArrivedDispatcher {
    private final MessageHandlerRegistry registry;
    private final ReplySender replySender;

    public MessageArrivedDispatcher(MessageHandlerRegistry registry, ReplySender replySender) {
        this.registry = registry;
        this.replySender = replySender;
    }

    public void dispatch(IncomingMessage message) {
        registry.find(message.interfaceName(), message.messageType())
                .ifPresent(handler -> handler.onMessageArrived(message, replySender));
    }
}
