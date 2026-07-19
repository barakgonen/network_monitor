package com.example.monitor.ingestion.udp;

import com.example.messagereader.api.*;
import com.example.messagereader.publisher.udp.UdpTrafficPublisher;
import com.example.monitor.callback.EventDispatcher;
import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.interfaces.InterfaceRuntimeState;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.reader.TrafficInterfaceDefinitionMapper;
import com.example.monitor.store.RecentMessageStore;
import com.example.monitor.time.ObservedTimeFormatter;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ReflectionUdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(ReflectionUdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ObservedTimeFormatter observedTimeFormatter;
    private final InterfaceRuntimeRegistry registry;
    private final TrafficReaderFactory trafficReaderFactory;
    private final TrafficInterfaceDefinitionMapper definitionMapper;
    private final EventDispatcher eventDispatcher;
    private final UdpTrafficPublisher udpTrafficPublisher;

    private volatile boolean running;

    public ReflectionUdpIngestionRunner(
            TrafficMonitorProperties properties,
            RecentMessageStore recentMessageStore,
            ObservedTimeFormatter observedTimeFormatter,
            InterfaceRuntimeRegistry registry,
            TrafficReaderFactory trafficReaderFactory,
            TrafficInterfaceDefinitionMapper definitionMapper,
            EventDispatcher eventDispatcher) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.observedTimeFormatter = observedTimeFormatter;
        this.registry = registry;
        this.trafficReaderFactory = trafficReaderFactory;
        this.definitionMapper = definitionMapper;
        this.eventDispatcher = eventDispatcher;
        this.udpTrafficPublisher = new UdpTrafficPublisher();
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

        TrafficReader reader = trafficReaderFactory.createReader(
                definitionMapper.toReaderDefinition(state.configuration()),
                properties.getUdp().getBufferSizeBytes(),
                parsed -> handleParsedMessage(state, parsed)
        );

        state.reader(reader);
        state.listening(true);
        reader.start();

        log.info("Started interface reader '{}' on port {}",
                state.configuration().getName(),
                state.configuration().getPort());
    }

    public synchronized void stopInterface(InterfaceRuntimeState state) {
        state.listening(false);

        TrafficReader reader = state.reader();

        if (reader != null) {
            reader.stop();
        }

        state.reader(null);

        log.info("Stopped interface reader '{}'", state.configuration().getName());
    }

    private void handleParsedMessage(InterfaceRuntimeState state, ParsedTrafficMessage parsed) {
        if (!running || !state.listening()) {
            return;
        }

        triggerCallbackForEvent(parsed);
        var configuration = state.configuration();
        if (configuration.isShouldBroadcast()) {
            configuration.getBroadcastTargets().forEach(target -> {
                var host = target.split(":")[0];
                var port = Integer.parseInt(target.split(":")[1]);
                PublishTarget publishTarget = new PublishTarget(TransportProtocol.UDP, host, port);
                udpTrafficPublisher.publish(publishTarget, parsed.rawPacket().payload());
                log.info("Broadcasting message to target {}:{}", host, port);
            });
        }
        ObservedMessage observedMessage = toObservedMessage(parsed);
        state.markReceived(observedMessage.parseError() != null);

        recentMessageStore.add(observedMessage);


        log.info("Received {} packet on port {} from {} - message={} - parseError={}",
                parsed.rawPacket().protocol(),
                parsed.rawPacket().localPort(),
                parsed.rawPacket().remoteAddress(),
                observedMessage.messageType(),
                observedMessage.parseError());
    }

    private void triggerCallbackForEvent(ParsedTrafficMessage parsed) {
        try {
            eventDispatcher.dispatch(parsed.parsedInstance());
        } catch (Exception e) {
            log.error("Exception occurred while processing parsed traffic message", e);
        }
    }

    private ObservedMessage toObservedMessage(ParsedTrafficMessage parsed) {
        byte[] payload = parsed.rawPacket().payload();

        Map<String, Object> header = new LinkedHashMap<>();
        header.put("parserMode", "message-reader-core");
        header.put("opcode", parsed.opcode());
        header.put("matchedClass", parsed.messageClassName());
        header.put("parsedHeader", parsed.headerFields());

        String parseError = parsed.parsed() ? null : parsed.parseError();
        Instant observedAt = parsed.rawPacket().receivedAt() == null ? Instant.now() : parsed.rawPacket().receivedAt();

        return new ObservedMessage(
                UUID.randomUUID().toString(),
                observedAt,
                observedTimeFormatter.format(observedAt),
                parsed.rawPacket().protocol().name(),
                parsed.rawPacket().remoteAddress(),
                parsed.rawPacket().localPort(),
                parsed.interfaceName(),
                parsed.messageName(),
                header,
                parsed.bodyFields(),
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

        log.info("Reflection ingestion stopped");
    }
}
