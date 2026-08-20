package com.example.monitor.interfaces;

import com.example.monitor.ingestion.rest.RestIngestionRunner;
import com.example.monitor.ingestion.tcp.TcpIngestionRunner;
import com.example.monitor.ingestion.udp.UdpIngestionRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterfaceControlService {
    private final InterfaceRuntimeRegistry runtimeRegistry;
    private final UdpIngestionRunner udpIngestionRunner;
    private final TcpIngestionRunner tcpIngestionRunner;
    private final RestIngestionRunner restIngestionRunner;

    public InterfaceControlService(
            InterfaceRuntimeRegistry runtimeRegistry,
            UdpIngestionRunner udpIngestionRunner,
            TcpIngestionRunner tcpIngestionRunner,
            RestIngestionRunner restIngestionRunner
    ) {
        this.runtimeRegistry = runtimeRegistry;
        this.udpIngestionRunner = udpIngestionRunner;
        this.tcpIngestionRunner = tcpIngestionRunner;
        this.restIngestionRunner = restIngestionRunner;
    }

    /** Every configured interface now has its own dedicated socket and runtime state. */
    public List<InterfaceStatusDto> statuses() {
        return runtimeRegistry.states().stream()
                .map(InterfaceStatusDto::from)
                .toList();
    }

    public void start(String key) {
        InterfaceRuntimeState state = requireState(key);

        switch (resolveTransport(state)) {
            case TCP -> tcpIngestionRunner.startInterface(state.config());
            case REST -> restIngestionRunner.startInterface(state.config());
            case UDP -> udpIngestionRunner.startInterface(state.config());
        }
    }

    public void stop(String key) {
        InterfaceRuntimeState state = requireState(key);

        switch (resolveTransport(state)) {
            case TCP -> tcpIngestionRunner.stopInterface(key);
            case REST -> restIngestionRunner.stopInterface(key);
            case UDP -> udpIngestionRunner.stopInterface(key);
        }
    }

    public void configure(String key, Integer port, String protocol, String mode, String host) {
        InterfaceRuntimeState state = requireState(key);
        state.configure(port, protocol, mode, host);
    }

    private InterfaceRuntimeState requireState(String key) {
        return runtimeRegistry.state(key)
                .orElseThrow(() -> new IllegalArgumentException("Unknown interface: " + key));
    }

    private enum TransportKind {
        UDP, TCP, REST
    }

    /** Dispatches to whichever runner matches the interface's *current* protocol - switchable at runtime via {@link #configure}. */
    private TransportKind resolveTransport(InterfaceRuntimeState state) {
        String protocol = state.config().getProtocol();

        if ("TCP".equalsIgnoreCase(protocol)) {
            return TransportKind.TCP;
        }
        if ("REST".equalsIgnoreCase(protocol)) {
            return TransportKind.REST;
        }
        if ("UDP".equalsIgnoreCase(protocol)) {
            return TransportKind.UDP;
        }

        throw new IllegalArgumentException("Unsupported protocol '" + protocol + "' for interface: " + state.config().getKey());
    }
}
