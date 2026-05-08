package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.interfaces.InterfaceRuntimeState;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.reflection.ReflectionMessageParser;
import com.example.monitor.reflection.ReflectionParseResult;
import com.example.monitor.store.RecentMessageStore;
import com.example.monitor.time.ObservedTimeFormatter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Component
public class ReflectionUdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(ReflectionUdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ReflectionMessageParser reflectionMessageParser;
    private final ObservedTimeFormatter observedTimeFormatter;
    private final InterfaceRuntimeRegistry registry;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile boolean running;

    public ReflectionUdpIngestionRunner(
            TrafficMonitorProperties properties,
            RecentMessageStore recentMessageStore,
            ReflectionMessageParser reflectionMessageParser,
            ObservedTimeFormatter observedTimeFormatter,
            InterfaceRuntimeRegistry registry
    ) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.reflectionMessageParser = reflectionMessageParser;
        this.observedTimeFormatter = observedTimeFormatter;
        this.registry = registry;
    }

    @PostConstruct
    public void start() {
        running = true;

        for (InterfaceRuntimeState state : registry.states()) {
            if (state.configuration().isEnabled()) {
                startInterface(state);
            }
        }
    }

    public synchronized void startInterface(InterfaceRuntimeState state) {
        if (state.listening()) {
            return;
        }

        Future<?> task = executor.submit(() -> listen(state));
        state.task(task);
        state.listening(true);
    }

    public synchronized void stopInterface(InterfaceRuntimeState state) {
        state.listening(false);

        DatagramSocket socket = state.socket();

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        Future<?> task = state.task();

        if (task != null) {
            task.cancel(true);
        }

        log.info("Stopped interface listener '{}'", state.configuration().getName());
    }

    private void listen(InterfaceRuntimeState state) {
        TrafficMonitorProperties.ReflectionInterface reflectionInterface = state.configuration();
        int port = reflectionInterface.getPort();
        int bufferSize = properties.getUdp().getBufferSizeBytes();

        try (DatagramSocket socket = new DatagramSocket(port)) {
            state.socket(socket);
            state.listening(true);

            log.info("Opcode-routed reflection interface '{}' UDP listener started on port {} with headerType={}, opcodeField={}, supportedOpcodes={}",
                    reflectionInterface.getName(),
                    port,
                    reflectionInterface.getHeaderType(),
                    reflectionInterface.getOpcodeFieldName(),
                    reflectionInterface.getSupportedMessages().keySet());

            while (running && state.listening()) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                ObservedMessage observedMessage = toObservedMessage(reflectionInterface, packet, payload);
                state.markReceived(observedMessage.parseError() != null);

                recentMessageStore.add(observedMessage);

                log.info("Reflection listener received packet on port {} from {}:{} - message={} - parseError={}",
                        port,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        observedMessage.messageType(),
                        observedMessage.parseError());
            }
        } catch (Exception e) {
            if (running && state.listening()) {
                log.error("Reflection UDP listener failed for port {}", port, e);
            }
        } finally {
            state.listening(false);
            state.socket(null);
        }
    }

    private ObservedMessage toObservedMessage(
            TrafficMonitorProperties.ReflectionInterface reflectionInterface,
            DatagramPacket packet,
            byte[] payload
    ) {
        ReflectionParseResult parseResult = reflectionMessageParser.parse(
                payload,
                reflectionInterface
        );

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("parserMode", "opcode-routed-reflection");
        header.put("headerType", reflectionInterface.getHeaderType());
        header.put("opcodeFieldName", reflectionInterface.getOpcodeFieldName());
        header.put("opcode", parseResult.opcode());
        header.put("matchedClass", parseResult.messageClassName());
        header.put("supportedOpcodes", reflectionInterface.getSupportedMessages().keySet());
        header.put("parsedHeader", parseResult.headerFields());

        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(parseResult.fields());

        String parseError = parseResult.parsed() ? null : parseResult.error();

        Instant observedAt = Instant.now();

        return new ObservedMessage(
                UUID.randomUUID().toString(),
                observedAt,
                observedTimeFormatter.format(observedAt),
                "UDP",
                packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                reflectionInterface.getPort(),
                reflectionInterface.getName(),
                parseResult.messageSimpleName(),
                header,
                body,
                payload.length,
                new String(payload, StandardCharsets.UTF_8),
                Base64.getEncoder().encodeToString(payload),
                parseError
        );
    }

    @PreDestroy
    public void stop() {
        running = false;

        for (InterfaceRuntimeState state : registry.states()) {
            stopInterface(state);
        }

        executor.shutdownNow();
        log.info("Reflection UDP ingestion stopped");
    }
}
