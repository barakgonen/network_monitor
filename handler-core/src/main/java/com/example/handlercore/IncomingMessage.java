package com.example.handlercore;

import java.util.Map;

public record IncomingMessage(
        String interfaceName,
        String messageType,
        String remoteHost,
        int remotePort,
        int localPort,
        long observedAtEpochMillis,
        Map<String, Object> header,
        Map<String, Object> body
) {
}
