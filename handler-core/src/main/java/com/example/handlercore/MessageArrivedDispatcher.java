package com.example.handlercore;

public final class MessageArrivedDispatcher {
    private final MessageHandlerRegistry registry;
    private final ReplySender replySender;

    public MessageArrivedDispatcher(MessageHandlerRegistry registry, ReplySender replySender) {
        this.registry = registry;
        this.replySender = replySender;
    }

    @SuppressWarnings("unchecked")
    public void dispatch(String interfaceName, String messageType, Object message, DestinationConfig destinationConfig) {
        registry.find(interfaceName, messageType).ifPresent(handler ->
                ((MessageArrivedHandler<Object>) handler).onMessageArrived(message, replySender, destinationConfig));
    }
}
