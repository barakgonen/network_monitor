package com.example.proxy.relay;

import com.example.proxy.config.EndpointConfig;
import com.example.proxy.config.RelayEntry;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Protocol/operation-unaware HTTP reverse proxy: forwards whatever method/path/body arrives to
 * {@code destination} and returns its response to the caller (the real leg, blocking), while
 * separately firing a best-effort duplicate of the same request at {@code mirror}
 * (fire-and-forget, response discarded).
 *
 * <p><b>Known limitation:</b> REST mirroring only ever mirrors the request direction. The
 * monitor's REST ingestion always synchronously auto-replies to whatever it receives (its own
 * configured/spec-derived response) - it has no concept of "observe an externally-produced HTTP
 * response as an event", unlike UDP/TCP where a reply is just more peer-to-peer bytes flowing
 * back through the relay and can be mirrored identically to a request. The destination's actual
 * response content is therefore never visible to the monitor via this mechanism; this asymmetry
 * with UDP/TCP is by design, not an oversight.</p>
 */
public class RestRelay implements Relay {

    private static final Set<String> HOP_BY_HOP_HEADERS =
            Set.of("host", "content-length", "connection", "transfer-encoding");

    private final RelayEntry config;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService mirrorExecutor = Executors.newCachedThreadPool();
    private ExecutorService serverExecutor;
    private HttpServer server;

    public RestRelay(RelayEntry config) {
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        EndpointConfig listen = config.getListen();
        server = HttpServer.create(new InetSocketAddress(listen.getHost(), listen.getPort()), 0);
        server.createContext("/", this::handle);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();

        System.out.println("[" + config.getKey() + "] REST relay listening on " + listen.getHost() + ":" + listen.getPort()
                + " -> destination " + config.getDestination().getHost() + ":" + config.getDestination().getPort()
                + " (mirror " + config.getMirror().getHost() + ":" + config.getMirror().getPort() + ")");
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        URI requestUri = exchange.getRequestURI();
        Map<String, List<String>> requestHeaders = exchange.getRequestHeaders();
        byte[] requestBody;
        try (InputStream in = exchange.getRequestBody()) {
            requestBody = in.readAllBytes();
        }

        mirrorExecutor.submit(() -> sendMirrorRequest(method, requestUri, requestHeaders, requestBody));

        try {
            HttpResponse<byte[]> response = forward(config.getDestination(), method, requestUri, requestHeaders, requestBody);
            copyResponse(exchange, response);
        } catch (Exception e) {
            System.err.println("[" + config.getKey() + "] REST relay destination call failed: " + e.getMessage());
            byte[] errorBody = "{\"error\":\"bad gateway\"}".getBytes();
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(502, errorBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(errorBody);
            }
        }
    }

    private HttpResponse<byte[]> forward(EndpointConfig target, String method, URI requestUri,
                                          Map<String, List<String>> headers, byte[] body)
            throws IOException, InterruptedException {
        URI uri = URI.create("http://" + target.getHost() + ":" + target.getPort() + requestUri);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body));
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (HOP_BY_HOP_HEADERS.contains(header.getKey().toLowerCase())) {
                continue;
            }
            for (String value : header.getValue()) {
                try {
                    builder.header(header.getKey(), value);
                } catch (IllegalArgumentException e) {
                    // HttpClient restricts a handful of headers (e.g. Upgrade) that can't be set
                    // via the builder - skip rather than fail the whole relay on those.
                }
            }
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private void sendMirrorRequest(String method, URI requestUri, Map<String, List<String>> headers, byte[] body) {
        try {
            forward(config.getMirror(), method, requestUri, headers, body);
        } catch (Exception e) {
            System.err.println("[" + config.getKey() + "] REST relay mirror send failed: " + e.getMessage());
        }
    }

    private void copyResponse(HttpExchange exchange, HttpResponse<byte[]> response) throws IOException {
        response.headers().map().forEach((name, values) -> {
            if (!HOP_BY_HOP_HEADERS.contains(name.toLowerCase())) {
                exchange.getResponseHeaders().put(name, values);
            }
        });
        byte[] body = response.body();
        exchange.sendResponseHeaders(response.statusCode(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        mirrorExecutor.shutdownNow();
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }
}
