package com.example.monitor.rest;

import com.example.monitor.TrafficMonitorTestApplication;
import com.example.monitor.store.RecentMessageStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end test of REST SERVER-mode ingestion: a real {@code RestIngestionRunner}-managed
 * embedded {@code HttpServer}, hit with a real HTTP client, decoded via the full parsing pipeline
 * (swagger file -&gt; RestApiDefinition -&gt; RestOperationRouter), landing in the real
 * {@link RecentMessageStore}, with the real default (schema-example-derived) auto-reply written
 * back. Lives in traffic-monitor-app-core (not traffic-monitor-app, where the rest of the IT
 * suite lives) because REST needs no concrete schema/handler classes at all - the reason that
 * split exists doesn't apply here.
 */
@SpringBootTest(classes = TrafficMonitorTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RestServerIngestionIT {

    @Value("${traffic.test.items-port}")
    private int itemsPort;

    @Autowired
    private RecentMessageStore recentMessageStore;

    @DynamicPropertySource
    static void configureDynamicPort(DynamicPropertyRegistry registry) throws IOException {
        int port = findFreePort();

        String yaml = """
                interfaces:
                  - key: items
                    name: Items REST Interface
                    protocol: REST
                    port: %d
                    swaggerFile: src/test/resources/rest/sample-openapi.yml
                """.formatted(port);

        Path tempConfig = Files.createTempFile("rest-server-it-", ".yml");
        Files.writeString(tempConfig, yaml);
        tempConfig.toFile().deleteOnExit();

        registry.add("traffic.tool.config-path", () -> tempConfig.toAbsolutePath().toString());
        registry.add("traffic.test.items-port", () -> port);
    }

    private static int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void getRequest_isIngested_andWrittenBackWithSchemaExampleAutoReply() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + itemsPort + "/items/42")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"id\":\"abc\"").contains("\"name\":\"Widget\"");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var match = recentMessageStore.recent().stream()
                    .filter(m -> "getItem".equals(m.messageType()))
                    .findFirst();

            assertThat(match).isPresent();
            assertThat(match.get().transportProtocol()).isEqualTo("REST");
            assertThat(match.get().interfaceName()).isEqualTo("Items REST Interface");
            assertThat(match.get().header()).containsEntry("itemId", "42");
            assertThat(match.get().parseError()).isNull();
        });
    }

    @Test
    void requestWithNoMatchingOperation_returns404_withoutCrashingServer() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + itemsPort + "/nope")).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);

        // Server must still work for the next request.
        HttpRequest followUp = HttpRequest.newBuilder(URI.create("http://localhost:" + itemsPort + "/items/1")).GET().build();
        HttpResponse<String> followUpResponse = client.send(followUp, HttpResponse.BodyHandlers.ofString());
        assertThat(followUpResponse.statusCode()).isEqualTo(200);
    }

    @Test
    void requestWithMalformedJsonBody_stillMatchesOperationAndRespondsButSetsParseError() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + itemsPort + "/items"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not valid json"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // The route still matched (POST /items -> createItem) and still gets its configured/
        // fallback response - a malformed body doesn't prevent a response, it's just recorded
        // as a parse error on the stored message.
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("\"status\":\"created\"");

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            var match = recentMessageStore.recent().stream()
                    .filter(m -> "createItem".equals(m.messageType()))
                    .findFirst();

            assertThat(match).isPresent();
            assertThat(match.get().parseError()).contains("Failed to parse JSON request body");
            assertThat(match.get().body()).isEmpty();
        });

        // Server must still work for the next (well-formed) request.
        HttpRequest followUp = HttpRequest.newBuilder(URI.create("http://localhost:" + itemsPort + "/items/1")).GET().build();
        HttpResponse<String> followUpResponse = client.send(followUp, HttpResponse.BodyHandlers.ofString());
        assertThat(followUpResponse.statusCode()).isEqualTo(200);
    }
}
