package com.example.monitor.publishing;

import com.example.monitor.rest.RestOperationDefinition;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestOperationInvokerTest {

    private final RestOperationInvoker invoker = new RestOperationInvoker(new ObjectMapper());
    private HttpServer stubServer;
    private int stubPort;

    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPathAndQuery = new AtomicReference<>();
    private final AtomicReference<String> capturedContentType = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

    private RestOperationDefinition operation(String httpMethod, String pathTemplate) {
        return new RestOperationDefinition(
                "items", "op", httpMethod, pathTemplate, List.of(), List.of(), List.of(), null, Map.of(), null, false);
    }

    @BeforeEach
    void startStubServer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            stubPort = socket.getLocalPort();
        }

        stubServer = HttpServer.create(new InetSocketAddress(stubPort), 0);
        stubServer.createContext("/", exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPathAndQuery.set(exchange.getRequestURI().getPath()
                    + (exchange.getRequestURI().getRawQuery() != null ? "?" + exchange.getRequestURI().getRawQuery() : ""));
            capturedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] responseBytes = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Stub", "yes");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.close();
        });
        stubServer.start();
    }

    @AfterEach
    void stopStubServer() {
        stubServer.stop(0);
    }

    @Test
    void invoke_substitutesPathParams_andEncodesQueryParams() {
        RestOperationDefinition getItem = operation("GET", "/items/{itemId}");

        RestInvocationResult result = invoker.invoke(
                "localhost", stubPort, getItem,
                Map.of("itemId", "42"), Map.of("filter", "a b&c"), Map.of());

        assertThat(capturedMethod.get()).isEqualTo("GET");
        assertThat(capturedPathAndQuery.get()).isEqualTo("/items/42?filter=a+b%26c");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(new String(result.bodyBytes(), StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
        assertThat(result.headers().get("X-Stub")).contains("yes");
        assertThat(result.parseError()).isNull();
    }

    @Test
    void invoke_withNonEmptyBody_sendsJsonContentTypeAndSerializedBody() {
        RestOperationDefinition createItem = operation("POST", "/items");

        invoker.invoke("localhost", stubPort, createItem, Map.of(), Map.of(), Map.of("name", "Widget"));

        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedContentType.get()).isEqualTo("application/json");
        assertThat(capturedBody.get()).contains("\"name\"").contains("\"Widget\"");
    }

    @Test
    void invoke_withEmptyBody_sendsNoContentTypeAndNoBody() {
        RestOperationDefinition getItem = operation("GET", "/items");

        invoker.invoke("localhost", stubPort, getItem, Map.of(), Map.of(), Map.of());

        assertThat(capturedContentType.get()).isNull();
        assertThat(capturedBody.get()).isEmpty();
    }

    @Test
    void invoke_whenConnectionFails_returnsResultWithParseErrorSet() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        // Socket closed immediately above - nothing listening on closedPort now.

        RestOperationDefinition getItem = operation("GET", "/items/1");

        RestInvocationResult result = invoker.invoke("localhost", closedPort, getItem, Map.of(), Map.of(), Map.of());

        assertThat(result.parseError()).isNotNull();
        assertThat(result.statusCode()).isEqualTo(0);
        assertThat(result.bodyBytes()).isEmpty();
    }
}
