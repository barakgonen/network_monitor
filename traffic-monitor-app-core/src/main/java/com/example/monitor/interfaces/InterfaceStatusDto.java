package com.example.monitor.interfaces;

import java.time.Instant;

public record InterfaceStatusDto(
        String key,
        String name,
        String protocol,
        int port,
        boolean listening,
        long receivedCount,
        long parseErrorCount,
        Instant lastObservedAt
) {
    public static InterfaceStatusDto from(InterfaceRuntimeState state) {
        return new InterfaceStatusDto(
                state.config().getKey(),
                state.config().getName(),
                state.config().getProtocol(),
                state.config().getPort(),
                state.isListening(),
                state.receivedCount(),
                state.parseErrorCount(),
                state.lastObservedAt()
        );
    }
}
