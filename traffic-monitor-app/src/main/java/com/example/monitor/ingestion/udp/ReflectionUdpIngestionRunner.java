package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.reflection.ReflectionMessageParser;
import com.example.monitor.reflection.ReflectionParseResult;
import com.example.monitor.store.RecentMessageStore;
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

@Component
public class ReflectionUdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(ReflectionUdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ReflectionMessageParser reflectionMessageParser;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private volatile boolean running;

    public ReflectionUdpIngestionRunner(
            TrafficMonitorProperties properties,
            RecentMessageStore recentMessageStore,
            ReflectionMessageParser reflectionMessageParser
    ) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.reflectionMessageParser = reflectionMessageParser;
    }

    @PostConstruct
    public void start() {
        running = true;

        for (TrafficMonitorProperties.ReflectionInterface reflectionInterface : properties.getReflectionInterfaces()) {
            if (!reflectionInterface.isEnabled()) {
                continue;
            }

            executor.submit(() -> listen(reflectionInterface));
        }
    }

    private void listen(TrafficMonitorProperties.ReflectionInterface reflectionInterface) {
        int port = reflectionInterface.getPort();
        int bufferSize = properties.getUdp().getBufferSizeBytes();

        try (DatagramSocket socket = new DatagramSocket(port)) {
            log.info("Opcode-routed reflection interface '{}' UDP listener started on port {} with headerType={}, opcodeField={}, supportedOpcodes={}",
                    reflectionInterface.getName(),
                    port,
                    reflectionInterface.getHeaderType(),
                    reflectionInterface.getOpcodeFieldName(),
                    reflectionInterface.getSupportedMessages().keySet());

            while (running) {
                byte[] buffer = new byte[bufferSize];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
                ObservedMessage observedMessage = toObservedMessage(reflectionInterface, packet, payload);

                recentMessageStore.add(observedMessage);

                log.info("Reflection listener received packet on port {} from {}:{} - message={} - parseError={}",
                        port,
                        packet.getAddress().getHostAddress(),
                        packet.getPort(),
                        observedMessage.messageType(),
                        observedMessage.parseError());
            }
        } catch (Exception e) {
            if (running) {
                log.error("Reflection UDP listener failed for port {}", port, e);
            }
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

        return new ObservedMessage(
                UUID.randomUUID().toString(),
                Instant.now(),
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
        executor.shutdownNow();
        log.info("Reflection UDP ingestion stopped");
    }
}
