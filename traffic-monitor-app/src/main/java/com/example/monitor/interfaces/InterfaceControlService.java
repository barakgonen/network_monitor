package com.example.monitor.interfaces;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.udp.ReflectionUdpIngestionRunner;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InterfaceControlService {
    private final InterfaceRuntimeRegistry registry;
    private final ReflectionUdpIngestionRunner reflectionUdpIngestionRunner;

    public InterfaceControlService(
            InterfaceRuntimeRegistry registry,
            ReflectionUdpIngestionRunner reflectionUdpIngestionRunner
    ) {
        this.registry = registry;
        this.reflectionUdpIngestionRunner = reflectionUdpIngestionRunner;
    }

    public List<InterfaceStatusDto> statuses() {
        return registry.statuses();
    }

    public InterfaceStatusDto start(String interfaceName) {
        InterfaceRuntimeState state = registry.state(interfaceName);
        reflectionUdpIngestionRunner.startInterface(state);
        return registry.status(interfaceName);
    }

    public InterfaceStatusDto stop(String interfaceName) {
        InterfaceRuntimeState state = registry.state(interfaceName);
        reflectionUdpIngestionRunner.stopInterface(state);
        return registry.status(interfaceName);
    }
}
