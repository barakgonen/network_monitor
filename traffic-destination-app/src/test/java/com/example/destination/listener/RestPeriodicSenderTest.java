package com.example.destination.listener;

import com.example.destination.config.InterfaceEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

class RestPeriodicSenderTest {

    private RestPeriodicSender sender;
    private CapturingServer server;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.stop();
        }
        if (server != null) {
            server.close();
        }
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

    private static InterfaceEntry entry(int port, String path, String body, long intervalMillis) {
        InterfaceEntry entry = new InterfaceEntry();
        entry.setKey("pets-reverse");
        entry.setProtocol("REST");
        entry.setMode("CLIENT");
        entry.setHost("127.0.0.1");
        entry.setPort(port);
        entry.setPath(path);
        entry.setRequestBody(body);
        entry.setIntervalMillis(intervalMillis);
        return entry;
    }

    @Test
    void sendsConfiguredRequestPeriodically() throws Exception {
        server = new CapturingServer();
        sender = new RestPeriodicSender(entry(server.port(), "/pets", "{\"name\":\"Whiskers\"}", 100));
        sender.start();

        awaitCondition(() -> server.requestCount() >= 2, Duration.ofSeconds(3));

        assertThat(server.lastMethod()).isEqualTo("POST");
        assertThat(server.lastPath()).isEqualTo("/pets");
        assertThat(server.lastBody()).isEqualTo("{\"name\":\"Whiskers\"}");
    }

    @Test
    void stop_haltsFurtherRequests() throws Exception {
        server = new CapturingServer();
        sender = new RestPeriodicSender(entry(server.port(), "/pets", "{}", 50));
        sender.start();

        awaitCondition(() -> server.requestCount() >= 1, Duration.ofSeconds(3));
        sender.stop();

        int countAtStop = server.requestCount();
        Thread.sleep(300);
        assertThat(server.requestCount()).isEqualTo(countAtStop);
    }

    private static final class CapturingServer implements AutoCloseable {
        private final HttpServer httpServer;
        private final List<String> methods = new CopyOnWriteArrayList<>();
        private final List<String> paths = new CopyOnWriteArrayList<>();
        private final List<String> bodies = new CopyOnWriteArrayList<>();

        CapturingServer() throws IOException {
            httpServer = HttpServer.create(new InetSocketAddress(0), 0);
            httpServer.createContext("/", this::handle);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
        }

        int port() {
            return httpServer.getAddress().getPort();
        }

        int requestCount() {
            return methods.size();
        }

        String lastMethod() {
            return methods.get(methods.size() - 1);
        }

        String lastPath() {
            return paths.get(paths.size() - 1);
        }

        String lastBody() {
            return bodies.get(bodies.size() - 1);
        }

        private void handle(HttpExchange exchange) throws IOException {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            methods.add(exchange.getRequestMethod());
            paths.add(exchange.getRequestURI().getPath());
            bodies.add(new String(body));

            byte[] response = "{}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        }

        @Override
        public void close() {
            httpServer.stop(0);
        }
    }
}
