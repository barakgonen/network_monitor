package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.config.ReplyMode;
import com.example.destination.listener.RestEchoListener;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.RestRelay;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
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
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real {@link RestRelay} wired against a real {@link RestEchoListener} (traffic-destination-app),
 * proving the reverse-HTTP-proxy path works against the actual destination-app JDK
 * {@code HttpServer} echo handler, not just a hand-rolled canned-response test double.
 */
class RestRelayIT {

    private RestRelay relay;
    private RestEchoListener destinationListener;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (relay != null) {
            relay.stop();
        }
        if (destinationListener != null) {
            destinationListener.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static EndpointConfig endpoint(String host, int port) {
        EndpointConfig endpoint = new EndpointConfig();
        endpoint.setHost(host);
        endpoint.setPort(port);
        return endpoint;
    }

    @Test
    void realDestinationAppEchoesPetsRequest_relayedAndMirroredCorrectly() throws Exception {
        int destinationPort = freePort();
        InterfaceEntry destinationConfig = new InterfaceEntry();
        destinationConfig.setKey("pets");
        destinationConfig.setProtocol("REST");
        destinationConfig.setPort(destinationPort);
        destinationConfig.setReplyMode(ReplyMode.ECHO);
        destinationListener = new RestEchoListener(destinationConfig);
        destinationListener.start();

        try (CapturingMirror mirror = new CapturingMirror()) {
            int listenPort = freePort();
            RelayEntry relayEntry = new RelayEntry();
            relayEntry.setKey("pets");
            relayEntry.setProtocol("REST");
            relayEntry.setListen(endpoint("0.0.0.0", listenPort));
            relayEntry.setDestination(endpoint("127.0.0.1", destinationPort));
            relayEntry.setMirror(endpoint("127.0.0.1", mirror.port()));
            relay = new RestRelay(relayEntry);
            relay.start();

            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + listenPort + "/pets"))
                    .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Fluffy\"}"))
                    .header("Content-Type", "application/json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // The real destination-app echoed the body back - proves the real leg round-tripped
            // through an actual JDK HttpServer instance, not a canned test double.
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo("{\"name\":\"Fluffy\"}");

            awaitMirrorRequest(mirror);
            assertThat(mirror.lastRequestBody()).isEqualTo("{\"name\":\"Fluffy\"}");
        }
    }

    private static void awaitMirrorRequest(CapturingMirror mirror) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (mirror.lastRequestBody() != null) {
                return;
            }
            Thread.sleep(20);
        }
        assertThat(mirror.lastRequestBody()).isNotNull();
    }

    private static final class CapturingMirror implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

        CapturingMirror() throws IOException {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", this::handle);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
        }

        int port() {
            return server.getAddress().getPort();
        }

        String lastRequestBody() {
            return lastRequestBody.get();
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            lastRequestBody.set(new String(body));

            byte[] response = "{}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
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
