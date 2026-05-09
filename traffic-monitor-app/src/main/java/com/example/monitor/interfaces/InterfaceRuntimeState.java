package com.example.monitor.interfaces;

import com.example.messagereader.api.TrafficReader;
import com.example.monitor.config.TrafficMonitorProperties;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class InterfaceRuntimeState {
    private final TrafficMonitorProperties.ReflectionInterface configuration;
    private volatile TrafficReader reader;
    private volatile boolean listening;
    private volatile Instant lastObservedAt;
    private final AtomicLong receivedCount = new AtomicLong(0);
    private final AtomicLong parseErrorCount = new AtomicLong(0);

    public InterfaceRuntimeState(TrafficMonitorProperties.ReflectionInterface configuration) {
        this.configuration = configuration;
    }

    public TrafficMonitorProperties.ReflectionInterface configuration() {
        return configuration;
    }

    public TrafficReader reader() {
        return reader;
    }

    public void reader(TrafficReader reader) {
        this.reader = reader;
    }

    public boolean listening() {
        return listening;
    }

    public void listening(boolean listening) {
        this.listening = listening;
    }

    public Instant lastObservedAt() {
        return lastObservedAt;
    }

    public void markReceived(boolean parseError) {
        this.lastObservedAt = Instant.now();
        this.receivedCount.incrementAndGet();

        if (parseError) {
            this.parseErrorCount.incrementAndGet();
        }
    }

    public long receivedCount() {
        return receivedCount.get();
    }

    public long parseErrorCount() {
        return parseErrorCount.get();
    }
}
