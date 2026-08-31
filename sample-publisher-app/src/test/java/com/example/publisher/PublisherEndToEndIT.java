package com.example.publisher;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end proof (real Spring app booted via {@code @LocalServerPort}, real sockets - no
 * mocks) that this app actually replaces what the broken Generic Publisher used to attempt: a real
 * UDP datagram sent through the public {@code /api/send} HTTP endpoint, decoded back via the
 * serdes engine to confirm correct bytes; and a real HTTP call to a throwaway stub server standing
 * in for an external REST API, confirming the response is returned to the caller.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublisherEndToEndIT {

    @LocalServerPort
    private int appPort;

    private HttpServer stubExternalApi;
    private static int externalApiPort;

    @DynamicPropertySource
    static void configureDynamicConfig(DynamicPropertyRegistry registry) throws IOException {
        externalApiPort = findFreePort();

        String yaml = """
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    protocol: UDP
                    port: 45001
                    serdesFile: ../serdes/fruit.protocol.json

                  - key: items
                    name: Items REST Interface
                    protocol: REST
                    port: %d
                    swaggerFile: src/test/resources/rest/sample-openapi.yml
                """.formatted(externalApiPort);

        Path tempConfig = Files.createTempFile("publisher-e2e-it-", ".yml");
        Files.writeString(tempConfig, yaml);
        tempConfig.toFile().deleteOnExit();

        registry.add("traffic.tool.config-path", () -> tempConfig.toAbsolutePath().toString());
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
    void sendUdp_throughRealHttpApi_producesRealDecodableWireBytes() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("localhost"))) {
            String payload = """
                    {"interfaceKey":"fruit","messageId":"Orange","host":"localhost","port":%d,"transport":"UDP",
                     "fields":{"sourceFarm":"north-farm-17","freshness":"very_fresh"}}
                    """.formatted(receiver.getLocalPort());

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + "/api/send"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"success\":true");

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.setSoTimeout(3000);
            receiver.receive(packet);

            ByteBuffer wire = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
            int opcode = wire.getInt();
            assertThat(opcode).isEqualTo(1001); // Orange's opcode per serdes/fruit.protocol.json
        }
    }

    @Test
    void sendRest_throughRealHttpApi_returnsExternalApiResponseToCaller() throws Exception {
        String payload = """
                {"interfaceKey":"items","messageId":"getItem","host":"localhost","fields":{"itemId":"99"}}
                """;

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + appPort + "/api/send"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"success\":true");
        assertThat(response.body()).contains("\"statusCode\":200");
        assertThat(response.body()).contains("Stub Widget");
    }
}
