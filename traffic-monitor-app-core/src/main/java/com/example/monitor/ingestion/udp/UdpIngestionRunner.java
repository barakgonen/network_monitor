package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

@Component
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final MessageIngestionPipeline pipeline;
    private final MeterRegistry meterRegistry;
    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;
    private final InterfaceRuntimeRegistry interfaceRuntimeRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DatagramSocket> sockets = new CopyOnWriteArrayList<>();
    private final Map<String, DatagramSocket> dedicatedSockets = new ConcurrentHashMap<>();

    private volatile boolean running;

    public UdpIngestionRunner(
            TrafficMonitorProperties properties,
            MessageIngestionPipeline pipeline,
            MeterRegistry meterRegistry,
            TrafficToolConfig trafficToolConfig,
            @Qualifier("interfaceMessageDefinitionRegistries") Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries,
            InterfaceRuntimeRegistry interfaceRuntimeRegistry
    ) {
        this.properties = properties;
        this.pipeline = pipeline;
        this.meterRegistry = meterRegistry;
        this.trafficToolConfig = trafficToolConfig;
        this.interfaceMessageDefinitionRegistries = interfaceMessageDefinitionRegistries;
        this.interfaceRuntimeRegistry = interfaceRuntimeRegistry;
    }

    @PostConstruct
    public void start() {
        running = true;

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (interfaceConfig.isEnabled() && "UDP".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                startInterface(interfaceConfig);
            }
        }
    }

    /**
     * Opens a dedicated socket for a single interface, independent of the others. Safe to call
     * again for an interface that's already listening (no-op).
     */
    public synchronized void startInterface(InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();

        if (dedicatedSockets.containsKey(key)) {
            return;
        }

        running = true;

        try {
            DatagramSocket socket = new DatagramSocket(interfaceConfig.getPort());
            dedicatedSockets.put(key, socket);
            sockets.add(socket);
            interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(true));
            executor.submit(() -> listenForInterface(interfaceConfig, socket));
        } catch (Exception e) {
            log.error("Failed to start UDP ingestion on port {} for interface {}",
                    interfaceConfig.getPort(), interfaceConfig.getName(), e);
            throw new IllegalStateException(
                    "Failed to start interface " + interfaceConfig.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Closes the dedicated socket for a single interface. Safe to call for an interface that
     * isn't currently listening (no-op).
     */
    public synchronized void stopInterface(String key) {
        DatagramSocket socket = dedicatedSockets.remove(key);

        if (socket != null) {
            sockets.remove(socket);
            if (!socket.isClosed()) {
                socket.close();
            }
        }

        interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(false));
    }

    private void listenForInterface(InterfaceConfig interfaceConfig, DatagramSocket socket) {
        int port = interfaceConfig.getPort();
        String key = interfaceConfig.getKey();
        MessageDefinitionRegistry scopedRegistry = interfaceMessageDefinitionRegistries.get(key);

        try {
            log.info("UDP ingestion started on port {} for interface {}", port, interfaceConfig.getName());

            receiveLoop(socket, port, () -> !socket.isClosed(), received -> {
                ObservedMessage message = pipeline.ingestForInterface(
                        received.payload(), "UDP", received.remoteAddress(), port, interfaceConfig, scopedRegistry);
                interfaceRuntimeRegistry.state(key)
                        .ifPresent(state -> state.recordObserved(message.parseError() != null));
                return message;
            });
        } catch (Exception e) {
            if (!socket.isClosed()) {
                log.error("UDP ingestion failed on port {} for interface {}", port, interfaceConfig.getName(), e);
                incrementListenerErrorCounter(port);
            }
        }
    }

    /**
     * Shared receive-decode-log loop used by every interface's dedicated-port listener.
     */
    private void receiveLoop(
            DatagramSocket socket, int port, BooleanSupplier keepRunning, Function<ReceivedPacket, ObservedMessage> ingest)
            throws IOException {
        int bufferSize = properties.getUdp().getBufferSizeBytes();

        while (keepRunning.getAsBoolean()) {
            ReceivedPacket received = receivePacket(socket, bufferSize);
            ObservedMessage message = ingest.apply(received);

            log.info("Received UDP {} message from {}:{} on port {} - {} bytes - type={} - parseError={}",
                    message.interfaceName(),
                    received.address().getHostAddress(),
                    received.remotePort(),
                    port,
                    received.payload().length,
                    message.messageType(),
                    message.parseError());
        }
    }

    private ReceivedPacket receivePacket(DatagramSocket socket, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
        String remoteAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
        return new ReceivedPacket(payload, remoteAddress, packet.getAddress(), packet.getPort());
    }

    private void incrementListenerErrorCounter(int port) {
        Counter.builder("network_monitor.udp.listener.errors")
                .tag("port", String.valueOf(port))
                .register(meterRegistry)
                .increment();
    }

    private record ReceivedPacket(byte[] payload, String remoteAddress, InetAddress address, int remotePort) {
    }

    @PreDestroy
    public void stop() {
        running = false;

        for (DatagramSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.close();
            }
        }

        dedicatedSockets.clear();
        executor.shutdownNow();
        log.info("UDP ingestion stopped");
    }
}
