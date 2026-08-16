package com.example.monitor;

import com.example.TrafficMonitorApplication;
import com.example.monitor.autoreply.AutoReplySettingsService;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.HistoryQuery;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.store.RecentMessageStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(
        classes = TrafficMonitorApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTestBase {

    @LocalServerPort
    protected int httpPort;

    /**
     * Every interface now owns its own port/protocol (see {@code com.example.monitor.schema.InterfaceConfig}),
     * sourced from a plain YAML file rather than Spring properties - so unlike the old
     * {@code @Value("${traffic.udp.fruit-port}")} fields, these can't be dynamically overridden
     * per-Spring-context out of the box. {@link #configureDynamicInterfacePorts} works around that
     * by generating a per-context temp copy of traffic-tool-test.yml with fresh free ports
     * substituted in, so distinct test contexts (e.g. the one {@code @AutoConfigureObservability}
     * creates for ActuatorEndToEndIT) never collide trying to bind the same fixed ports while both
     * are alive. Deliberately kept as instance fields resolved via {@code @Value}, not static -
     * static fields shared across contexts get clobbered by whichever context's
     * {@code @DynamicPropertySource} method runs last (bit us before, see git history).
     */
    @Value("${traffic.test.fruit-port}")
    protected int fruitPort;

    @Value("${traffic.test.ping-port}")
    protected int pingPort;

    @Value("${traffic.test.weather-port}")
    protected int weatherPort;

    @Value("${traffic.test.candy-port}")
    protected int candyPort;

    @Value("${traffic.test.rada-port}")
    protected int radaPort;

    @Value("${traffic.test.rada-le-port}")
    protected int radaLePort;

    @Autowired
    protected RecentMessageStore recentMessageStore;

    @Autowired
    protected AutoReplySettingsService autoReplySettingsService;

    @Autowired
    protected MessageArchiveRepository messageArchiveRepository;

    /**
     * Spring Boot 4 dropped {@code TestRestTemplate} (and the {@code @SpringBootTest} wiring that
     * auto-provided it); see {@link TestHttpClient} for why this is a plain field populated in
     * {@code @BeforeEach} rather than an {@code @Autowired} bean.
     */
    protected TestHttpClient restTemplate;

    @Autowired
    protected MeterRegistry meterRegistry;

    @BeforeEach
    void initializeTestHttpClient() {
        restTemplate = new TestHttpClient();
    }

    @DynamicPropertySource
    static void configureDynamicInterfacePorts(DynamicPropertyRegistry registry) throws IOException {
        int fruitPort = findFreePort();
        int pingPort = findFreePort();
        int weatherPort = findFreePort();
        int candyPort = findFreePort();
        int radaPort = findFreePort();
        int radaLePort = findFreePort();

        String yaml = loadTemplateYaml()
                .replace("port: 25001", "port: " + fruitPort)
                .replace("port: 25002", "port: " + pingPort)
                .replace("port: 25003", "port: " + weatherPort)
                .replace("port: 25004", "port: " + candyPort)
                .replace("port: 25050", "port: " + radaPort)
                .replace("port: 25051", "port: " + radaLePort);

        Path tempConfig = Files.createTempFile("traffic-tool-test-", ".yml");
        Files.writeString(tempConfig, yaml);
        tempConfig.toFile().deleteOnExit();

        registry.add("traffic.tool.config-path", () -> tempConfig.toAbsolutePath().toString());
        registry.add("traffic.test.fruit-port", () -> fruitPort);
        registry.add("traffic.test.ping-port", () -> pingPort);
        registry.add("traffic.test.weather-port", () -> weatherPort);
        registry.add("traffic.test.candy-port", () -> candyPort);
        registry.add("traffic.test.rada-port", () -> radaPort);
        registry.add("traffic.test.rada-le-port", () -> radaLePort);
    }

    private static String loadTemplateYaml() throws IOException {
        try (var in = AbstractIntegrationTestBase.class.getClassLoader().getResourceAsStream("traffic-tool-test.yml")) {
            if (in == null) {
                throw new IllegalStateException("traffic-tool-test.yml not found on test classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
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
