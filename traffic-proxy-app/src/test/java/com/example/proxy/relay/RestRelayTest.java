package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class RestRelayTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static RelayEntry relayEntry(int listenPort, int destinationPort, int mirrorPort) {
        RelayEntry entry = new RelayEntry();
        entry.setKey("pets");
        entry.setProtocol("REST");
        entry.setListen(endpoint("0.0.0.0", listenPort));
        entry.setDestination(endpoint("127.0.0.1", destinationPort));
        entry.setMirror(endpoint("127.0.0.1", mirrorPort));
        return entry;
    }

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    private static void awaitCondition(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @Test
    void forwardsRequestToDestination_returnsItsResponse_andMirrorsRequest() throws Exception {
        CannedHttpServer destination = new CannedHttpServer(200, "{\"from\":\"destination\"}");
        CannedHttpServer mirror = new CannedHttpServer(200, "{\"from\":\"mirror\"}");
        int listenPort = freePort();
        RestRelay relay = new RestRelay(relayEntry(listenPort, destination.port(), mirror.port()));
        relay.start();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listenPort + "/pets/1"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Fluffy\"}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"from\":\"destination\"}");

            awaitCondition(() -> mirror.lastRequestBody() != null, Duration.ofSeconds(3));
            assertThat(mirror.lastRequestBody()).isEqualTo("{\"name\":\"Fluffy\"}");
            assertThat(mirror.lastRequestPath()).isEqualTo("/pets/1");
        } finally {
            relay.stop();
            destination.close();
            mirror.close();
        }
    }

    @Test
    void destinationDown_returns502_regardlessOfMirrorHealth() throws Exception {
        int unusedDestinationPort = freePort();
        CannedHttpServer mirror = new CannedHttpServer(200, "{}");
        int listenPort = freePort();
        RestRelay relay = new RestRelay(relayEntry(listenPort, unusedDestinationPort, mirror.port()));
        relay.start();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listenPort + "/pets"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(502);
        } finally {
            relay.stop();
            mirror.close();
        }
    }

    @Test
    void mirrorDown_destinationResponseStillReturnedCorrectly() throws Exception {
        CannedHttpServer destination = new CannedHttpServer(200, "{\"from\":\"destination\"}");
        int unusedMirrorPort = freePort();
        int listenPort = freePort();
        RestRelay relay = new RestRelay(relayEntry(listenPort, destination.port(), unusedMirrorPort));
        relay.start();

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listenPort + "/pets"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"from\":\"destination\"}");
        } finally {
            relay.stop();
            destination.close();
        }
    }

    private static class CannedHttpServer implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
        private final AtomicReference<String> lastRequestPath = new AtomicReference<>();

        CannedHttpServer(int status, String responseBody) throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", exchange -> handle(exchange, status, responseBody));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        String lastRequestBody() {
            return lastRequestBody.get();
        }

        String lastRequestPath() {
            return lastRequestPath.get();
        }

        private void handle(HttpExchange exchange, int status, String responseBody) throws IOException {
            byte[] requestBody;
            try (InputStream in = exchange.getRequestBody()) {
                requestBody = in.readAllBytes();
            }
            lastRequestBody.set(new String(requestBody));
            lastRequestPath.set(exchange.getRequestURI().getPath());

            byte[] response = responseBody.getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
