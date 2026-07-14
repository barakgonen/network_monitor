package com.example.handlercore;

import com.example.schemacore.ProtocolMessage;

public interface ReplySender {
    void reply(ProtocolMessage message, String host, int port);
}
