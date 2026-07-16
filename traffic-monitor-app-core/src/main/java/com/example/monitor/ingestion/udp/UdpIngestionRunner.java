package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.model.ObservedMessage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final MessageIngestionPipeline pipeline;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DatagramSocket> sockets = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    public UdpIngestionRunner(TrafficMonitorProperties properties, MessageIngestionPipeline pipeline, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.pipeline = pipeline;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        if (!properties.getUdp().isEnabled()) {
            log.info("UDP ingestion is disabled");
            return;
        }

        running = true;
        executor.submit(() -> listen(properties.getUdp().getFruitPort()));
        executor.submit(() -> listen(properties.getUdp().getWeatherPort()));
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

    @PreDestroy
    public void stop() {
        running = false;

        for (DatagramSocket socket : sockets) {
            if (!socket.isClosed()) {
                socket.close();
            }
        }

        executor.shutdownNow();
        log.info("UDP ingestion stopped");
    }
}
