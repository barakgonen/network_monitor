package com.example.monitor.rest;

import com.example.monitor.TrafficMonitorTestApplication;
import com.example.monitor.store.RecentMessageStore;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of REST CLIENT-mode / on-demand publishing: a real throwaway
 * {@code HttpServer} standing in for "the external API," a real POST to
 * {@code /api/publisher/send}, and an assertion that the external API's response was captured as
 * a newly-observed message - the entire point of REST client mode, per the design (unlike
 * fire-and-forget UDP/TCP publishing).
 */
@SpringBootTest(classes = TrafficMonitorTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestClientPublishingIT {

    @LocalServerPort
    private int appPort;

    @Value("${traffic.test.external-api-port}")
    private int externalApiPort;

    @Autowired
    private RecentMessageStore recentMessageStore;

    private HttpServer stubExternalApi;

    @DynamicPropertySource
    static void configureDynamicPort(DynamicPropertyRegistry registry) throws IOException {
        int port = findFreePort();

        String yaml = """
                interfaces:
                  - key: items
                    name: Items REST Interface
                    protocol: REST
                    mode: CLIENT
                    host: localhost
                    port: %d
                    swaggerFile: src/test/resources/rest/sample-openapi.yml
                """.formatted(port);

        Path tempConfig = Files.createTempFile("rest-client-it-", ".yml");
        Files.writeString(tempConfig, yaml);
        tempConfig.toFile().deleteOnExit();

        registry.add("traffic.tool.config-path", () -> tempConfig.toAbsolutePath().toString());
        registry.add("traffic.test.external-api-port", () -> port);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeEach
    void startStubExternalApi() throws IOException {
        stubExternalApi = HttpServer.create(new InetSocketAddress(externalApiPort), 0);
        stubExternalApi.createContext("/items/99", exchange -> {
            byte[] body = "{\"id\":\"99\",\"name\":\"Stub Widget\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        stubExternalApi.start();
    }

    @AfterEach
    void stopStubExternalApi() {
        stubExternalApi.stop(0);
    }

    @Test
    void publishingGetItem_capturesExternalApiResponse_asNewlyObservedMessage() throws Exception {
        String payload = """
                {"interfaceKey":"items","messageType":"getItem","fields":{"itemId":"99"}}
                """;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + "/api/publisher/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":true");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var match = recentMessageStore.recent().stream()
                    .filter(m -> "getItem (response)".equals(m.messageType()))
                    .findFirst();

            assertThat(match).isPresent();
            assertThat(match.get().transportProtocol()).isEqualTo("REST");
            assertThat(match.get().body()).containsEntry("name", "Stub Widget");
        });
    }
}
