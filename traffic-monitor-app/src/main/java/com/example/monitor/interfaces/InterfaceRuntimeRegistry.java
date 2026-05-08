package com.example.monitor.interfaces;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.time.ObservedTimeFormatter;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InterfaceRuntimeRegistry {
    private final TrafficMonitorProperties properties;
    private final ObservedTimeFormatter observedTimeFormatter;
    private final Map<String, InterfaceRuntimeState> states = new ConcurrentHashMap<>();

    public InterfaceRuntimeRegistry(TrafficMonitorProperties properties, ObservedTimeFormatter observedTimeFormatter) {
        this.properties = properties;
        this.observedTimeFormatter = observedTimeFormatter;

        for (TrafficMonitorProperties.ReflectionInterface reflectionInterface : properties.getReflectionInterfaces()) {
            states.put(reflectionInterface.getName(), new InterfaceRuntimeState(reflectionInterface));
        }
    }

    public List<InterfaceRuntimeState> states() {
        return states.values()
                .stream()
                .sorted(Comparator.comparing(state -> state.configuration().getName()))
                .toList();
    }

    public InterfaceRuntimeState state(String interfaceName) {
        InterfaceRuntimeState state = states.get(interfaceName);

        if (state == null) {
            throw new IllegalArgumentException("Unknown interface: " + interfaceName);
        }

        return state;
    }

    public List<InterfaceStatusDto> statuses() {
        return states()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public InterfaceStatusDto status(String interfaceName) {
        return toDto(state(interfaceName));
    }

    private InterfaceStatusDto toDto(InterfaceRuntimeState state) {
        int activeWindowSeconds = properties.getInterfaceActiveWindowSeconds();
        Instant lastObservedAt = state.lastObservedAt();

        boolean trafficRecentlyObserved =
                lastObservedAt != null
                        && Duration.between(lastObservedAt, Instant.now()).getSeconds() <= activeWindowSeconds;

        return new InterfaceStatusDto(
                state.configuration().getName(),
                state.configuration().getProtocol(),
                state.configuration().getPort(),
                state.listening(),
                trafficRecentlyObserved,
                observedTimeFormatter.format(lastObservedAt),
                state.receivedCount(),
                state.parseErrorCount(),
                activeWindowSeconds
        );
    }
}
