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

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        if (properties.getUdp().isEnabled()) {
            executor.submit(() -> listen(properties.getUdp().getFruitPort()));
            executor.submit(() -> listen(properties.getUdp().getWeatherPort()));
        } else {
            log.info("UDP ingestion is disabled");
        }

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (interfaceConfig.hasDedicatedPort() && interfaceConfig.isEnabled()
                    && "UDP".equalsIgnoreCase(interfaceConfig.getProtocol())) {
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

    private void listen(int port) {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            sockets.add(socket);
            log.info("UDP ingestion started on port {}", port);

            int bufferSize = properties.getUdp().getBufferSizeBytes();

            while (running) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                String remoteAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                ObservedMessage message = pipeline.ingest(payload, "UDP", remoteAddress, port);

                log.info("Received UDP {} message from {}:{} on port {} - {} bytes - type={} - parseError={}",
                        message.interfaceName(),
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        port,
                        payload.length,
                        message.messageType(),
                        message.parseError());
            }
        } catch (Exception e) {
            if (running) {
                log.error("UDP ingestion failed on port {}", port, e);
                Counter.builder("network_monitor.udp.listener.errors")
                        .tag("port", String.valueOf(port))
                        .register(meterRegistry)
                        .increment();
            }
        }
    }

    private void listenForInterface(InterfaceConfig interfaceConfig, DatagramSocket socket) {
        int port = interfaceConfig.getPort();
        String key = interfaceConfig.getKey();
        MessageDefinitionRegistry scopedRegistry = interfaceMessageDefinitionRegistries.get(key);

        try {
            log.info("UDP ingestion started on port {} for interface {}", port, interfaceConfig.getName());

            int bufferSize = properties.getUdp().getBufferSizeBytes();

            while (!socket.isClosed()) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                String remoteAddress = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                ObservedMessage message = pipeline.ingestForInterface(
                        payload, "UDP", remoteAddress, port, interfaceConfig, scopedRegistry);

                interfaceRuntimeRegistry.state(key)
                        .ifPresent(state -> state.recordObserved(message.parseError() != null));

                log.info("Received UDP {} message from {}:{} on port {} - {} bytes - type={} - parseError={}",
                        message.interfaceName(),
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        port,
                        payload.length,
                        message.messageType(),
                        message.parseError());
            }
        } catch (Exception e) {
            if (!socket.isClosed()) {
                log.error("UDP ingestion failed on port {} for interface {}", port, interfaceConfig.getName(), e);
                Counter.builder("network_monitor.udp.listener.errors")
                        .tag("port", String.valueOf(port))
                        .register(meterRegistry)
                        .increment();
            }
        }
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
