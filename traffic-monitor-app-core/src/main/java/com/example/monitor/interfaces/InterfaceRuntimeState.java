package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runtime status for a single dedicated-port interface: whether its socket is currently open and
 * counters/timestamps updated as messages arrive.
 */
public class InterfaceRuntimeState {
    private final InterfaceConfig config;
    private volatile boolean listening;
    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong parseErrorCount = new AtomicLong();
    private volatile Instant lastObservedAt;

    public InterfaceRuntimeState(InterfaceConfig config) {
        this.config = config;
    }

    public InterfaceConfig config() {
        return config;
    }

    public boolean isListening() {
        return listening;
    }

    public void setListening(boolean listening) {
        this.listening = listening;
    }

    public long receivedCount() {
        return receivedCount.get();
    }

    public long parseErrorCount() {
        return parseErrorCount.get();
    }

    public Instant lastObservedAt() {
        return lastObservedAt;
    }

    public void recordObserved(boolean parseError) {
        receivedCount.incrementAndGet();
        if (parseError) {
            parseErrorCount.incrementAndGet();
        }
        lastObservedAt = Instant.now();
    }
}
