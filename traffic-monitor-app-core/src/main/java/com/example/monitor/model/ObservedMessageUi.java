package com.example.monitor.model;

import java.time.Instant;
import java.util.Map;

public record ObservedMessageUi(
        String id,
        Instant observedAt,
        String protocol,
        String interfaceName,
        String messageType,
        Map<String, Object> body,
        String parseError
) {
    public static ObservedMessageUi from(ObservedMessage message) {
        return new ObservedMessageUi(
                message.id(),
                message.observedAt(),
                message.transportProtocol(),
                message.interfaceName(),
                message.messageType(),
                message.body(),
                message.parseError()
        );
    }
}
