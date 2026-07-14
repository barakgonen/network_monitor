package com.example.handlercore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MessageHandlerRegistry {
    private final Map<String, MessageArrivedHandler> handlersByKey;

    public MessageHandlerRegistry(List<MessageArrivedHandler> handlers) {
        Map<String, MessageArrivedHandler> byKey = new HashMap<>();

        for (MessageArrivedHandler handler : handlers) {
            String key = key(handler.interfaceName(), handler.messageType());

            if (byKey.putIfAbsent(key, handler) != null) {
                throw new IllegalStateException("Duplicate MessageArrivedHandler registered for " + key);
            }
        }

        this.handlersByKey = Map.copyOf(byKey);
    }

    public Optional<MessageArrivedHandler> find(String interfaceName, String messageType) {
        return Optional.ofNullable(handlersByKey.get(key(interfaceName, messageType)));
    }

    private static String key(String interfaceName, String messageType) {
        return interfaceName + "::" + messageType;
    }
}
