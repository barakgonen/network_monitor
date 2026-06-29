package com.example.monitor.callback;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EventDispatcher {

    private final Map<Class<?>, EventBindingRegistry.EventBinding<?>> bindingsByMessageType;

    public EventDispatcher(List<EventBindingConfigurer> configurers) {
        EventBindingRegistry registry = new EventBindingRegistry();

        for (EventBindingConfigurer configurer : configurers) {
            configurer.configure(registry);
        }

        this.bindingsByMessageType = new LinkedHashMap<>(registry.bindings());
    }

    public void dispatch(Object message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }

        Class<?> messageType = message.getClass();

        EventBindingRegistry.EventBinding<?> binding =
                bindingsByMessageType.get(messageType);

        if (binding == null) {
            throw new IllegalArgumentException(
                    "No event binding registered for message type: "
                            + messageType.getName()
            );
        }

        dispatchInternal(binding, message);
    }

    private <T> void dispatchInternal(
            EventBindingRegistry.EventBinding<T> binding,
            Object message
    ) {
        T typedMessage = binding.messageType().cast(message);
        binding.handler().handle(typedMessage);
    }
}