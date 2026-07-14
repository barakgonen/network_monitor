package com.example.handlercore;

public interface MessageArrivedHandler<T> {
    String interfaceName();

    String messageType();

    /**
     * @param destinationConfig the configured auto-reply destination for this interface, or
     *                          {@code null} if none is registered.
     */
    void onMessageArrived(T message, ReplySender replySender, DestinationConfig destinationConfig);
}
