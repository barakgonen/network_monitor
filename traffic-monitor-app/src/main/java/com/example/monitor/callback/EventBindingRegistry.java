package com.example.monitor.callback;

import java.util.LinkedHashMap;
import java.util.Map;

public class EventBindingRegistry {

    private final Map<Class<?>, EventBinding<?>> bindings = new LinkedHashMap<>();

    public <T> EventBindingRegistry bind(
            Class<T> messageType,
            String eventKey,
            MessageHandler<? super T> handler
    ) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType cannot be null");
        }

        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }

        EventBinding<T> binding = new EventBinding<>(
                messageType,
                new EventKey(eventKey),
                handler
        );

        EventBinding<?> previous = bindings.putIfAbsent(messageType, binding);

        if (previous != null) {
            throw new IllegalStateException(
                    "Duplicate event binding for message type: "
                            + messageType.getName()
                            + ". Existing event key: "
                            + previous.eventKey().value()
                            + ", new event key: "
                            + eventKey
            );
        }

        return this;
    }

    Map<Class<?>, EventBinding<?>> bindings() {
        return Map.copyOf(bindings);
    }

    public record EventBinding<T>(
            Class<T> messageType,
            EventKey eventKey,
            MessageHandler<? super T> handler
    ) {
    }
}
