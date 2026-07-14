package com.example.handlercore;

public interface MessageArrivedHandler {
    String interfaceName();

    String messageType();

    void onMessageArrived(IncomingMessage message, ReplySender replySender);
}
