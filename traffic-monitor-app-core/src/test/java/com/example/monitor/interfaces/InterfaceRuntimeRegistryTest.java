package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceRuntimeRegistryTest {

    @Test
    void everyConfiguredInterface_getsRuntimeState() {
        InterfaceConfig fruit = new InterfaceConfig();
        fruit.setKey("fruit");
        fruit.setPort(5001);

        InterfaceConfig rada = new InterfaceConfig();
        rada.setKey("rada");
        rada.setPort(5050);

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(fruit, rada));

        InterfaceRuntimeRegistry registry = new InterfaceRuntimeRegistry(config);

        assertThat(registry.states()).hasSize(2);
        assertThat(registry.state("rada")).isPresent();
        assertThat(registry.state("fruit")).isPresent();
    }
}
