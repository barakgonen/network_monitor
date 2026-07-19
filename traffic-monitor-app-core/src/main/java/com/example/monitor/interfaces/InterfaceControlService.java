package com.example.monitor.interfaces;

import com.example.monitor.ingestion.udp.UdpIngestionRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterfaceControlService {
    private final InterfaceRuntimeRegistry runtimeRegistry;
    private final UdpIngestionRunner udpIngestionRunner;

    public InterfaceControlService(InterfaceRuntimeRegistry runtimeRegistry, UdpIngestionRunner udpIngestionRunner) {
        this.runtimeRegistry = runtimeRegistry;
        this.udpIngestionRunner = udpIngestionRunner;
    }

    public List<InterfaceStatusDto> statuses() {
        return runtimeRegistry.states().stream().map(InterfaceStatusDto::from).toList();
    }

    public void start(String key) {
        InterfaceRuntimeState state = requireState(key);
        requireUdp(state);
        udpIngestionRunner.startInterface(state.config());
    }

    public void stop(String key) {
        InterfaceRuntimeState state = requireState(key);
        requireUdp(state);
        udpIngestionRunner.stopInterface(key);
    }

    private InterfaceRuntimeState requireState(String key) {
        return runtimeRegistry.state(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or non-dedicated-port interface: " + key));
    }

    private void requireUdp(InterfaceRuntimeState state) {
        if (!"UDP".equalsIgnoreCase(state.config().getProtocol())) {
            throw new IllegalArgumentException(
                    "Runtime start/stop is only supported for UDP dedicated-port interfaces: " + state.config().getKey());
        }
    }
}
