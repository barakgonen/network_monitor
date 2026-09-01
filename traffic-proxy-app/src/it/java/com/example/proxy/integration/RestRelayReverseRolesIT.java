package com.example.proxy.integration;

import com.example.destination.config.InterfaceEntry;
import com.example.destination.listener.RestPeriodicSender;
import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.example.proxy.relay.RestRelay;
import com.example.tester.rest.RestListener;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The REST role-reversal case, end to end, using the actual production classes from all three
 * apps: a real {@link RestPeriodicSender} (traffic-destination-app, acting as the REST client
 * that sends requests on a timer), a real {@link RestRelay} (traffic-proxy-app - no code changes
 * needed for this case, since the relay's own accept-and-forward role is identical regardless of
 * which app is on which end), and a real {@link RestListener} (traffic-tester-app, acting as the
 * REST server that echoes the body back) - proving the three real components interoperate for
 * the reversed roles, not just each side's own unit tests against hand-rolled doubles.
 */
class RestRelayReverseRolesIT {

    private RestPeriodicSender sender;
    private RestRelay relay;
    private RestListener testerListener;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.stop();
        }
        if (relay != null) {
            relay.stop();
        }
        if (testerListener != null) {
            testerListener.close();
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
    void destinationAppClient_relay_testerAppServer_fullRoundTripAndMirror() throws Exception {
        int relayListenPort = freePort();
        int testerServerPort = freePort();
        CapturingMirror mirror = new CapturingMirror();

        // 1. traffic-tester-app: real REST server, echoes the body back.
        testerListener = new RestListener(testerServerPort);
        testerListener.start();

        // 2. traffic-proxy-app: real relay, plain forward config (no reverse-mode code needed).
        RelayEntry relayEntry = new RelayEntry();
        relayEntry.setKey("pets-reverse");
        relayEntry.setProtocol("REST");
        relayEntry.setListen(endpoint("0.0.0.0", relayListenPort));
        relayEntry.setDestination(endpoint("127.0.0.1", testerServerPort));
        relayEntry.setMirror(endpoint("127.0.0.1", mirror.port()));
        relay = new RestRelay(relayEntry);
        relay.start();

        // 3. traffic-destination-app: real periodic REST client.
        InterfaceEntry senderConfig = new InterfaceEntry();
        senderConfig.setKey("pets-reverse");
        senderConfig.setProtocol("REST");
        senderConfig.setMode("CLIENT");
        senderConfig.setHost("127.0.0.1");
        senderConfig.setPort(relayListenPort);
        senderConfig.setPath("/pets");
        senderConfig.setRequestBody("{\"name\":\"Whiskers\"}");
        senderConfig.setIntervalMillis(100);
        sender = new RestPeriodicSender(senderConfig);
        sender.start();

        // The periodic request flows destination-app -> relay -> tester-app's server -> echoed
        // back through the relay, and gets mirrored along the way.
        awaitCondition(() -> mirror.requestCount() >= 2, Duration.ofSeconds(5));
        assertThat(mirror.lastRequestBody()).isEqualTo("{\"name\":\"Whiskers\"}");
        assertThat(mirror.lastRequestPath()).isEqualTo("/pets");
    }

    private static final class CapturingMirror implements AutoCloseable {
        private final HttpServer server;
        private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
        private final AtomicReference<String> lastRequestPath = new AtomicReference<>();
        private final AtomicReference<Integer> requestCount = new AtomicReference<>(0);

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

        String lastRequestPath() {
            return lastRequestPath.get();
        }

        int requestCount() {
            return requestCount.get();
        }

        private synchronized void handle(HttpExchange exchange) throws IOException {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            lastRequestBody.set(new String(body));
            lastRequestPath.set(exchange.getRequestURI().getPath());
            requestCount.set(requestCount.get() + 1);

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
