package com.example.monitor.ingestion.udp;

import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import com.example.monitor.time.ObservedTimeFormatter;
import com.example.schemas.fruit.FruitProtocolCodec;
import com.example.schemas.fruit.FruitProtocolHeader;
import com.example.schemas.weather.WeatherProtocolCodec;
import com.example.schemas.weather.WeatherProtocolHeader;
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
public class UdpIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(UdpIngestionRunner.class);

    private final TrafficMonitorProperties properties;
    private final RecentMessageStore recentMessageStore;
    private final ObservedTimeFormatter observedTimeFormatter;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    private final FruitProtocolCodec fruitProtocolCodec = new FruitProtocolCodec();
    private final WeatherProtocolCodec weatherProtocolCodec = new WeatherProtocolCodec();

    private volatile boolean running;
    private DatagramSocket fruitSocket;
    private DatagramSocket weatherSocket;

    public UdpIngestionRunner(TrafficMonitorProperties properties, RecentMessageStore recentMessageStore, ObservedTimeFormatter observedTimeFormatter) {
        this.properties = properties;
        this.recentMessageStore = recentMessageStore;
        this.observedTimeFormatter = observedTimeFormatter;
    }

    @PostConstruct
    public void start() {
        if (!properties.getUdp().isEnabled()) {
            log.info("UDP ingestion is disabled");
            return;
        }

        running = true;
        executor.submit(() -> listenFruitPort(properties.getUdp().getFruitPort()));
        executor.submit(() -> listenWeatherPort(properties.getUdp().getWeatherPort()));
    }

    private void listenFruitPort(int port) {
        try {
            fruitSocket = new DatagramSocket(port);
            log.info("Fruit Interface UDP ingestion started on port {}", port);
            listen(fruitSocket, port, "FRUIT");
        } catch (Exception e) {
            if (running) {
                log.error("Fruit UDP ingestion failed", e);
            }
        }
    }

    private void listenWeatherPort(int port) {
        try {
            weatherSocket = new DatagramSocket(port);
            log.info("Weather Interface UDP ingestion started on port {}", port);
            listen(weatherSocket, port, "WEATHER");
        } catch (Exception e) {
            if (running) {
                log.error("Weather UDP ingestion failed", e);
            }
        }
    }

    private void listen(DatagramSocket socket, int port, String interfaceKey) throws Exception {
        int bufferSize = properties.getUdp().getBufferSizeBytes();

        while (running) {
            byte[] buffer = new byte[bufferSize];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);

            byte[] payload = Arrays.copyOf(packet.getData(), packet.getLength());
            ObservedMessage message = toObservedMessage(packet, port, interfaceKey, payload);
            recentMessageStore.add(message);

            log.info("Received UDP {} message from {}:{} on port {} - {} bytes - type={} - parseError={}",
                    message.interfaceName(),
                    packet.getAddress().getHostAddress(),
                    packet.getPort(),
                    port,
                    payload.length,
                    message.messageType(),
                    message.parseError());
        }
    }

    private ObservedMessage toObservedMessage(
            DatagramPacket packet,
            int localPort,
            String interfaceKey,
            byte[] payload
    ) {
        String payloadText = new String(payload, StandardCharsets.UTF_8);
        String payloadBase64 = Base64.getEncoder().encodeToString(payload);

        String interfaceName = interfaceKey.equals("WEATHER") ? "Weather Interface" : "Fruit Interface";
        String messageType = "Unknown";
        Map<String, Object> header = new LinkedHashMap<>();
        Map<String, Object> body = new LinkedHashMap<>();
        String parseError = null;

        try {
            if (interfaceKey.equals("WEATHER")) {
                WeatherProtocolCodec.DecodedWeatherMessage decoded = weatherProtocolCodec.decode(payload);
                WeatherProtocolHeader decodedHeader = decoded.header();

                interfaceName = decoded.interfaceName();
                messageType = decoded.messageType();

                header.put("opcode", decodedHeader.opcode());
                header.put("sendTimeEpochMillis", decodedHeader.sendTimeEpochMillis());
                header.put("bodyLength", decodedHeader.bodyLength());
                body.putAll(decoded.bodyFields());
            } else {
                FruitProtocolCodec.DecodedFruitMessage decoded = fruitProtocolCodec.decode(payload);
                FruitProtocolHeader decodedHeader = decoded.header();

                interfaceName = decoded.interfaceName();
                messageType = decoded.messageType();

                header.put("opcode", decodedHeader.opcode());
                header.put("sendTimeEpochMillis", decodedHeader.sendTimeEpochMillis());
                header.put("bodyLength", decodedHeader.bodyLength());
                body.putAll(decoded.bodyFields());
            }
        } catch (Exception e) {
            parseError = e.getMessage();
        }

        Instant observedAt = Instant.now();

        return new ObservedMessage(
                UUID.randomUUID().toString(),
                observedAt,
                observedTimeFormatter.format(observedAt),
                "UDP",
                packet.getAddress().getHostAddress() + ":" + packet.getPort(),
                localPort,
                interfaceName,
                messageType,
                header,
                body,
                payload.length,
                payloadText,
                payloadBase64,
                parseError
        );
    }

    @PreDestroy
    public void stop() {
        running = false;

        closeSocket(fruitSocket);
        closeSocket(weatherSocket);

        executor.shutdownNow();
        log.info("UDP ingestion stopped");
    }

    private void closeSocket(DatagramSocket socket) {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
