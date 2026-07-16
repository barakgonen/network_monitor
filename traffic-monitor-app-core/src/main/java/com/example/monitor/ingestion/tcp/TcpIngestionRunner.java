package com.example.monitor.ingestion.tcp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.model.ObservedMessage;
import com.example.schemacore.ProtocolHeaderCodec;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TcpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(TcpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final MessageIngestionPipeline pipeline;
    private final MeterRegistry meterRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<ServerSocket> serverSockets = new CopyOnWriteArrayList<>();
    private final List<Socket> activeConnections = new CopyOnWriteArrayList<>();

    private volatile boolean running;

    public TcpIngestionRunner(TrafficMonitorProperties properties, MessageIngestionPipeline pipeline, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.pipeline = pipeline;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void start() {
        Gauge.builder("network_monitor.tcp.connections.active", activeConnections, List::size)
                .register(meterRegistry);

        if (!properties.getTcp().isEnabled()) {
            log.info("TCP ingestion is disabled");
            return;
        }

        running = true;
        executor.submit(() -> acceptLoop(properties.getTcp().getFruitPort()));
        executor.submit(() -> acceptLoop(properties.getTcp().getWeatherPort()));
    }

    private void acceptLoop(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSockets.add(serverSocket);
            log.info("TCP ingestion started on port {}", port);

            while (running) {
                Socket connection = serverSocket.accept();
                activeConnections.add(connection);
                Counter.builder("network_monitor.tcp.connections.accepted")
                        .tag("port", String.valueOf(port))
                        .register(meterRegistry)
                        .increment();
                executor.submit(() -> handleConnection(connection, port));
            }
        } catch (Exception e) {
            if (running) {
                log.error("TCP ingestion failed on port {}", port, e);
            }
        }
    }

    private void handleConnection(Socket connection, int port) {
        try (connection; DataInputStream in = new DataInputStream(new BufferedInputStream(connection.getInputStream()))) {
            String remoteAddress = connection.getInetAddress().getHostAddress() + ":" + connection.getPort();

            while (running) {
                byte[] payload;
                try {
                    payload = readOneMessage(in);
                } catch (EOFException e) {
                    break;
                }

                ObservedMessage message = pipeline.ingest(payload, "TCP", remoteAddress, port);

                log.info("Received TCP {} message from {} on port {} - {} bytes - type={} - parseError={}",
                        message.interfaceName(),
                        remoteAddress,
                        port,
                        payload.length,
                        message.messageType(),
                        message.parseError());
            }
        } catch (Exception e) {
            if (running) {
                log.warn("TCP connection handling failed on port {}", port, e);
                Counter.builder("network_monitor.tcp.connections.errors")
                        .tag("port", String.valueOf(port))
                        .register(meterRegistry)
                        .increment();
            }
        } finally {
            activeConnections.remove(connection);
        }
    }

    private byte[] readOneMessage(DataInputStream in) throws IOException {
        byte[] headerBytes = new byte[ProtocolHeaderCodec.HEADER_SIZE_BYTES];
        in.readFully(headerBytes);

        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);
        headerBuffer.getInt();
        headerBuffer.getLong();
        int bodyLength = headerBuffer.getInt();

        int maxBodyLengthBytes = properties.getTcp().getMaxBodyLengthBytes();
        if (bodyLength < 0 || bodyLength > maxBodyLengthBytes) {
            throw new IOException("Invalid TCP bodyLength: " + bodyLength + " (max " + maxBodyLengthBytes + ")");
        }

        byte[] bodyBytes = new byte[bodyLength];
        in.readFully(bodyBytes);

        byte[] payload = new byte[headerBytes.length + bodyBytes.length];
        System.arraycopy(headerBytes, 0, payload, 0, headerBytes.length);
        System.arraycopy(bodyBytes, 0, payload, headerBytes.length, bodyBytes.length);
        return payload;
    }

    @PreDestroy
    public void stop() {
        running = false;

        for (ServerSocket serverSocket : serverSockets) {
            if (!serverSocket.isClosed()) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    log.warn("Failed to close TCP server socket", e);
                }
            }
        }

        for (Socket connection : activeConnections) {
            if (!connection.isClosed()) {
                try {
                    connection.close();
                } catch (IOException e) {
                    log.warn("Failed to close TCP connection", e);
                }
            }
        }

        executor.shutdownNow();
        log.info("TCP ingestion stopped");
    }
}
