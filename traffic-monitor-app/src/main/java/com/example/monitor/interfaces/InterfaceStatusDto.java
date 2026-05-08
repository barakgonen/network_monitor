package com.example.monitor.interfaces;

public record InterfaceStatusDto(
        String name,
        String protocol,
        int port,
        boolean listening,
        boolean trafficRecentlyObserved,
        String lastObservedAtDisplay,
        long receivedCount,
        long parseErrorCount,
        int activeWindowSeconds
) {
}
