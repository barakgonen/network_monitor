package com.example.monitor.interfaces;

import com.example.monitor.ingestion.tcp.TcpIngestionRunner;
import com.example.monitor.ingestion.udp.UdpIngestionRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InterfaceControlService {
    private final InterfaceRuntimeRegistry runtimeRegistry;
    private final UdpIngestionRunner udpIngestionRunner;
    private final TcpIngestionRunner tcpIngestionRunner;

    public InterfaceControlService(
            InterfaceRuntimeRegistry runtimeRegistry,
            UdpIngestionRunner udpIngestionRunner,
            TcpIngestionRunner tcpIngestionRunner
    ) {
        this.runtimeRegistry = runtimeRegistry;
        this.udpIngestionRunner = udpIngestionRunner;
        this.tcpIngestionRunner = tcpIngestionRunner;
    }

    /** Every configured interface now has its own dedicated socket and runtime state. */
    public List<InterfaceStatusDto> statuses() {
        return runtimeRegistry.states().stream()
                .map(InterfaceStatusDto::from)
                .toList();
    }

    public void start(String key) {
        InterfaceRuntimeState state = requireState(key);

        if (isTcp(state)) {
            tcpIngestionRunner.startInterface(state.config());
        } else {
            udpIngestionRunner.startInterface(state.config());
        }
    }

    public void stop(String key) {
        InterfaceRuntimeState state = requireState(key);

        if (isTcp(state)) {
            tcpIngestionRunner.stopInterface(key);
        } else {
            udpIngestionRunner.stopInterface(key);
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

    /** Dispatches to whichever runner matches the interface's *current* protocol - switchable at runtime via {@link #configure}. */
    private boolean isTcp(InterfaceRuntimeState state) {
        String protocol = state.config().getProtocol();

        if ("TCP".equalsIgnoreCase(protocol)) {
            return true;
        }
        if ("UDP".equalsIgnoreCase(protocol)) {
            return false;
        }

        throw new IllegalArgumentException("Unsupported protocol '" + protocol + "' for interface: " + state.config().getKey());
    }
}
