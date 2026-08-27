package com.example.tester.rest;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * REST server-mode counterpart to {@code TcpListener}: binds and echoes any incoming request's
 * body back as the response - needed when the peer this interface talks to (e.g.
 * traffic-proxy-app's RestRelay in a reversed-role relay entry) is itself the one sending
 * requests rather than receiving them. Unlike TCP, HTTP has no way for a server to push data
 * unprompted, so - unlike {@code TcpListener} - this never sends anything until asked.
 */
public class RestListener implements AutoCloseable {

    private final int port;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;

    public RestListener(int port) {
        this.port = port;
    }

    public void start() throws IOException {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Tester REST server listener started on port " + port);
    }

    public void await(Duration duration) throws InterruptedException {
        Instant deadline = Instant.now().plus(duration);

        while (running.get() && Instant.now().isBefore(deadline)) {
            Thread.sleep(250);
        }

        close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body;
        try (InputStream in = exchange.getRequestBody()) {
            body = in.readAllBytes();
        }

        System.out.println();
        System.out.println("=== REST MESSAGE ARRIVED TO TESTER ===");
        System.out.println("From: " + exchange.getRemoteAddress());
        System.out.println("Request: " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
        System.out.println("Bytes: " + body.length);
        System.out.println("Text: " + new String(body, StandardCharsets.UTF_8));
        System.out.println("=====================================");
        System.out.println();

        List<String> contentType = exchange.getRequestHeaders().get("Content-Type");
        String type = (contentType != null && !contentType.isEmpty()) ? contentType.get(0) : "application/json";
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void close() {
        running.set(false);

        if (server != null) {
            server.stop(0);
        }

        System.out.println("Tester REST listener stopped");
    }
}
