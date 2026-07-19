package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceRuntimeRegistryTest {

    @Test
    void onlyDedicatedPortInterfaces_getRuntimeState() {
        InterfaceConfig legacy = new InterfaceConfig();
        legacy.setKey("fruit");

        InterfaceConfig dedicated = new InterfaceConfig();
        dedicated.setKey("rada");
        dedicated.setPort(5050);

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(legacy, dedicated));

        InterfaceRuntimeRegistry registry = new InterfaceRuntimeRegistry(config);

        assertThat(registry.states()).hasSize(1);
        assertThat(registry.state("rada")).isPresent();
        assertThat(registry.state("fruit")).isEmpty();
    }
}
