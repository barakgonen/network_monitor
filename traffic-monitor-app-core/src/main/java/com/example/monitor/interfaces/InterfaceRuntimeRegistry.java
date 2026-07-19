package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One {@link InterfaceRuntimeState} per dedicated-port interface, built once at startup from
 * {@link TrafficToolConfig}. Legacy shared-port interfaces (no dedicated port configured) aren't
 * individually controllable and have no entry here.
 */
@Component
public class InterfaceRuntimeRegistry {
    private final Map<String, InterfaceRuntimeState> states;

    public InterfaceRuntimeRegistry(TrafficToolConfig trafficToolConfig) {
        Map<String, InterfaceRuntimeState> map = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (interfaceConfig.hasDedicatedPort()) {
                map.put(interfaceConfig.getKey(), new InterfaceRuntimeState(interfaceConfig));
            }
        }

        this.states = Collections.unmodifiableMap(map);
    }

    public Collection<InterfaceRuntimeState> states() {
        return states.values();
    }

    public Optional<InterfaceRuntimeState> state(String key) {
        return Optional.ofNullable(states.get(key));
    }
}
