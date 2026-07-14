package com.example.handlercore;

import java.util.Map;

public interface ReplySender {
    void reply(String interfaceName, String messageType, Map<String, Object> fields, String host, int port);
}
