package com.example.monitor;

import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.HistoryQuery;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.store.RecentMessageStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTestBase {

    @LocalServerPort
    protected int httpPort;

    @Value("${traffic.udp.fruit-port}")
    protected int fruitPort;

    @Value("${traffic.udp.weather-port}")
    protected int weatherPort;

    @Value("${traffic.tcp.fruit-port}")
    protected int tcpFruitPort;

    @Value("${traffic.tcp.weather-port}")
    protected int tcpWeatherPort;

    @Autowired
    protected RecentMessageStore recentMessageStore;

    @Autowired
    protected AutoReplySettingsService autoReplySettingsService;

    @Autowired
    protected MessageArchiveRepository messageArchiveRepository;

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected MeterRegistry meterRegistry;

    @DynamicPropertySource
    static void registerUdpPorts(DynamicPropertyRegistry registry) throws IOException {
        int fruitPort = findFreePort();
        int weatherPort = findFreePort();
        registry.add("traffic.udp.fruit-port", () -> fruitPort);
        registry.add("traffic.udp.weather-port", () -> weatherPort);
    }

    @DynamicPropertySource
    static void registerTcpPorts(DynamicPropertyRegistry registry) throws IOException {
        int tcpFruitPort = findFreePort();
        int tcpWeatherPort = findFreePort();
        registry.add("traffic.tcp.fruit-port", () -> tcpFruitPort);
        registry.add("traffic.tcp.weather-port", () -> tcpWeatherPort);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    protected void sendUdp(int port, byte[] payload) throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            InetAddress address = InetAddress.getByName("localhost");
            DatagramPacket packet = new DatagramPacket(payload, payload.length, address, port);
            socket.send(packet);
        }
    }

    protected void sendTcp(int port, byte[] payload) throws IOException {
        sendTcpMultiple(port, payload);
    }

    protected void sendTcpMultiple(int port, byte[]... payloads) throws IOException {
        try (Socket socket = new Socket("localhost", port)) {
            for (byte[] payload : payloads) {
                socket.getOutputStream().write(payload);
            }
            socket.getOutputStream().flush();
        }
    }

    protected ObservedMessage awaitStoreContains(Predicate<ObservedMessage> predicate) {
        AtomicReference<ObservedMessage> found = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            Optional<ObservedMessage> match = recentMessageStore.recent().stream().filter(predicate).findFirst();
            assertThat(match).isPresent();
            found.set(match.get());
        });

        return found.get();
    }

    protected ObservedMessage awaitHistoryContains(Predicate<ObservedMessage> predicate) {
        AtomicReference<ObservedMessage> found = new AtomicReference<>();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            HistoryQuery query = new HistoryQuery(null, null, false, null, null, 500, 0);
            Optional<ObservedMessage> match = messageArchiveRepository.findHistory(query).items()
                    .stream().filter(predicate).findFirst();
            assertThat(match).isPresent();
            found.set(match.get());
        });

        return found.get();
    }

    protected String httpUrl(String path) {
        return "http://localhost:" + httpPort + path;
    }
}
