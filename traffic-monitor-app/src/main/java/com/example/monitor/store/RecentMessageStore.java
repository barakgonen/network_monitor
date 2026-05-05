package com.example.monitor.store;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

@Component
public class RecentMessageStore {
    private final ArrayDeque<ObservedMessage> messages = new ArrayDeque<>();
    private final int maxSize;

    public RecentMessageStore(TrafficMonitorProperties properties) {
        this.maxSize = properties.getStore().getMaxSize();
    }

    public synchronized void add(ObservedMessage message) {
        messages.addFirst(message);

        while (messages.size() > maxSize) {
            messages.removeLast();
        }
    }

    public synchronized List<ObservedMessage> recent() {
        return new ArrayList<>(messages);
    }
}
