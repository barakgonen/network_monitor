package com.example.monitor.ingestion.tcp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.reflect.ReflectiveFieldExtractor;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemacore.reflect.StructSizeCalculator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class TcpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(TcpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final MessageIngestionPipeline pipeline;
    private final MeterRegistry meterRegistry;
    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;
    private final InterfaceRuntimeRegistry interfaceRuntimeRegistry;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<Socket> activeConnections = new CopyOnWriteArrayList<>();
    private final Map<String, ServerSocket> dedicatedServerSockets = new ConcurrentHashMap<>();
    private final Map<String, List<Socket>> dedicatedConnections = new ConcurrentHashMap<>();
    private final Map<String, AtomicBoolean> dedicatedClientStopFlags = new ConcurrentHashMap<>();

    public TcpIngestionRunner(
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
        Gauge.builder("network_monitor.tcp.connections.active", activeConnections, List::size)
                .register(meterRegistry);

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (interfaceConfig.isEnabled() && "TCP".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                startInterface(interfaceConfig);
            }
        }
    }

    /**
     * Starts a single interface, independent of the others. In {@code SERVER} mode (default)
     * this binds a dedicated server socket, failing fast if the bind itself fails. In {@code
     * CLIENT} mode this instead kicks off a background reconnect loop that connects out to
     * {@link InterfaceConfig#getHost()}:{@link InterfaceConfig#getPort()} - it does not throw if
     * the remote isn't reachable yet, since that's an expected transient state, not a startup
     * failure. Safe to call again for an interface that's already running (no-op).
     */
    public synchronized void startInterface(InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();

        if (dedicatedServerSockets.containsKey(key) || dedicatedClientStopFlags.containsKey(key)) {
            return;
        }

        if ("CLIENT".equalsIgnoreCase(interfaceConfig.getMode())) {
            startClientInterface(interfaceConfig);
        } else {
            startServerInterface(interfaceConfig);
        }
    }

    private void startServerInterface(InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();

        try {
            ServerSocket serverSocket = new ServerSocket(interfaceConfig.getPort());
            dedicatedServerSockets.put(key, serverSocket);
            dedicatedConnections.put(key, new CopyOnWriteArrayList<>());
            interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(true));
            executor.submit(() -> acceptLoopForInterface(interfaceConfig, serverSocket));
        } catch (Exception e) {
            log.error("Failed to start TCP ingestion on port {} for interface {}",
                    interfaceConfig.getPort(), interfaceConfig.getName(), e);
            throw new IllegalStateException(
                    "Failed to start interface " + interfaceConfig.getName() + ": " + e.getMessage(), e);
        }
    }

    private void startClientInterface(InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        dedicatedClientStopFlags.put(key, stopFlag);
        dedicatedConnections.put(key, new CopyOnWriteArrayList<>());
        interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(true));
        executor.submit(() -> connectLoopForInterface(interfaceConfig, stopFlag));
    }

    /**
     * Closes the dedicated server socket/reconnect loop and any open connections for a single
     * interface. Safe to call for an interface that isn't currently running (no-op). For a
     * {@code CLIENT}-mode interface, this has a worst-case latency bounded by {@code
     * traffic.tcp.client-connect-timeout-ms} - a connect attempt already in flight has no live
     * socket yet to force-close, so the reconnect loop only observes the stop flag once that
     * attempt resolves (success or timeout).
     */
    public synchronized void stopInterface(String key) {
        ServerSocket serverSocket = dedicatedServerSockets.remove(key);

        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Failed to close TCP server socket for interface {}", key, e);
            }
        }

        AtomicBoolean stopFlag = dedicatedClientStopFlags.remove(key);
        if (stopFlag != null) {
            stopFlag.set(true);
        }

        List<Socket> connections = dedicatedConnections.remove(key);
        if (connections != null) {
            for (Socket connection : connections) {
                activeConnections.remove(connection);
                if (!connection.isClosed()) {
                    try {
                        connection.close();
                    } catch (IOException e) {
                        log.warn("Failed to close TCP connection for interface {}", key, e);
                    }
                }
            }
        }

        interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(false));
    }

    private void acceptLoopForInterface(InterfaceConfig interfaceConfig, ServerSocket serverSocket) {
        int port = interfaceConfig.getPort();
        String key = interfaceConfig.getKey();

        try {
            log.info("TCP ingestion started on port {} for interface {}", port, interfaceConfig.getName());

            while (!serverSocket.isClosed()) {
                Socket connection = serverSocket.accept();
                activeConnections.add(connection);
                List<Socket> connections = dedicatedConnections.get(key);
                if (connections != null) {
                    connections.add(connection);
                }
                incrementAcceptedCounter(port);
                executor.submit(() -> handleConnectionForInterface(connection, interfaceConfig));
            }
        } catch (Exception e) {
            if (!serverSocket.isClosed()) {
                log.error("TCP ingestion failed on port {} for interface {}", port, interfaceConfig.getName(), e);
                incrementListenerErrorCounter(port);
            }
        }
    }

    /**
     * Repeatedly connects out to {@link InterfaceConfig#getHost()}:{@link
     * InterfaceConfig#getPort()}, handing each successful connection to the same {@link
     * #handleConnectionForInterface} used by server mode, and retrying (after {@code
     * clientReconnectDelayMs}) once that connection ends - until {@code stopFlag} is set.
     */
    private void connectLoopForInterface(InterfaceConfig interfaceConfig, AtomicBoolean stopFlag) {
        String key = interfaceConfig.getKey();
        String host = interfaceConfig.getHost();
        int port = interfaceConfig.getPort();
        int connectTimeoutMs = properties.getTcp().getClientConnectTimeoutMs();
        int reconnectDelayMs = properties.getTcp().getClientReconnectDelayMs();

        while (!stopFlag.get()) {
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);

                List<Socket> connections = dedicatedConnections.get(key);
                if (stopFlag.get() || connections == null) {
                    closeQuietly(socket);
                    break;
                }

                activeConnections.add(socket);
                connections.add(socket);
                incrementAcceptedCounter(port);
                log.info("TCP client connected for interface {} to {}:{}", key, host, port);
                handleConnectionForInterface(socket, interfaceConfig);
            } catch (IOException e) {
                closeQuietly(socket);
                log.debug("TCP client connect attempt failed for interface {} ({}:{}), retrying", key, host, port, e);
                incrementReconnectAttemptCounter(port);
            }

            if (!stopFlag.get()) {
                try {
                    Thread.sleep(reconnectDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                log.warn("Failed to close TCP client socket", e);
            }
        }
    }

    private void handleConnectionForInterface(Socket connection, InterfaceConfig interfaceConfig) {
        int port = interfaceConfig.getPort();
        String key = interfaceConfig.getKey();
        MessageDefinitionRegistry scopedRegistry = interfaceMessageDefinitionRegistries.get(key);

        try (connection; DataInputStream in = new DataInputStream(new BufferedInputStream(connection.getInputStream()))) {
            String remoteAddress = connection.getInetAddress().getHostAddress() + ":" + connection.getPort();

            while (!connection.isClosed()) {
                byte[] payload;
                try {
                    payload = readOneMessageGeneric(in, interfaceConfig);
                } catch (EOFException e) {
                    break;
                }

                ObservedMessage message = pipeline.ingestForInterface(
                        payload, "TCP", remoteAddress, port, interfaceConfig, scopedRegistry);
                interfaceRuntimeRegistry.state(key)
                        .ifPresent(state -> state.recordObserved(message.parseError() != null));

                log.info("Received TCP {} message from {} on port {} - {} bytes - type={} - parseError={}",
                        message.interfaceName(),
                        remoteAddress,
                        port,
                        payload.length,
                        message.messageType(),
                        message.parseError());
            }
        } catch (Exception e) {
            if (!connection.isClosed()) {
                log.warn("TCP connection handling failed on port {} for interface {}", port, interfaceConfig.getName(), e);
                incrementConnectionErrorCounter(port);
            }
        } finally {
            activeConnections.remove(connection);
            List<Socket> connections = dedicatedConnections.get(key);
            if (connections != null) {
                connections.remove(connection);
            }
        }
    }

    /**
     * Frames one message off a TCP byte stream: decodes the interface's configured header type
     * (reflectively, same as the UDP dedicated-port decode path), reads the body length out of it
     * via {@link InterfaceConfig#getBodyLengthFieldName()}, then reads that many more bytes. UDP
     * doesn't need this since each datagram is already framed one-per-packet; a TCP byte stream
     * has no such boundary, so the header must carry an explicit body length.
     */
    private byte[] readOneMessageGeneric(DataInputStream in, InterfaceConfig interfaceConfig) throws IOException {
        Class<?> headerType;
        try {
            headerType = Class.forName(interfaceConfig.getHeaderType());
        } catch (ClassNotFoundException e) {
            throw new IOException("Unknown header type: " + interfaceConfig.getHeaderType(), e);
        }

        int headerSize = StructSizeCalculator.calculateStructSize(headerType);
        byte[] headerBytes = new byte[headerSize];
        in.readFully(headerBytes);

        Map<String, Object> headerFields;
        try {
            Object header = ReflectiveStructCodec.decode(headerType, headerBytes, interfaceConfig.resolveByteOrder());
            headerFields = ReflectiveFieldExtractor.extractFields(header);
        } catch (Exception e) {
            throw new IOException(
                    "Failed to decode TCP header for interface " + interfaceConfig.getName() + ": " + e.getMessage(), e);
        }

        Object bodyLengthValue = headerFields.get(interfaceConfig.getBodyLengthFieldName());
        int bodyLength = Integer.parseInt(String.valueOf(bodyLengthValue));

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

    private void incrementAcceptedCounter(int port) {
        Counter.builder("network_monitor.tcp.connections.accepted")
                .tag("port", String.valueOf(port))
                .register(meterRegistry)
                .increment();
    }

    private void incrementConnectionErrorCounter(int port) {
        Counter.builder("network_monitor.tcp.connections.errors")
                .tag("port", String.valueOf(port))
                .register(meterRegistry)
                .increment();
    }

    private void incrementListenerErrorCounter(int port) {
        Counter.builder("network_monitor.tcp.listener.errors")
                .tag("port", String.valueOf(port))
                .register(meterRegistry)
                .increment();
    }

    private void incrementReconnectAttemptCounter(int port) {
        Counter.builder("network_monitor.tcp.client.reconnect.attempts")
                .tag("port", String.valueOf(port))
                .register(meterRegistry)
                .increment();
    }

    @PreDestroy
    public void stop() {
        for (AtomicBoolean stopFlag : dedicatedClientStopFlags.values()) {
            stopFlag.set(true);
        }

        for (ServerSocket serverSocket : dedicatedServerSockets.values()) {
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

        dedicatedServerSockets.clear();
        dedicatedConnections.clear();
        dedicatedClientStopFlags.clear();
        executor.shutdownNow();
        log.info("TCP ingestion stopped");
    }
}
