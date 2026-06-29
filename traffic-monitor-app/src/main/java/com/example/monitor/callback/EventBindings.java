package com.example.monitor.callback;

import java.util.ArrayList;
import java.util.List;

public class EventBindings {

    private final List<MessageEventBinding<?>> bindings = new ArrayList<>();

    public <T> EventBindings bind(
            Class<T> messageType,
            String eventKey,
            MessageHandler<? super T> handler
    ) {
        bindings.add(new SimpleMessageEventBinding<>(
                messageType,
                new EventKey(eventKey),
                handler
        ));

        return this;
    }

    public List<MessageEventBinding<?>> bindings() {
        return List.copyOf(bindings);
    }
}