package com.example.monitor.ingestion.rest;

import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestAutoReplySettingsService;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestOperationRouter;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST analogue of {@code TcpIngestionRunner}: one dedicated embedded {@link HttpServer} per
 * {@code SERVER}-mode REST interface, mirroring the "every dedicated-port interface gets its own
 * socket" pattern. Unlike TCP, there's no per-connection framing loop to run - {@code HttpServer}
 * already frames one request per {@link com.sun.net.httpserver.HttpHandler} invocation, so this
 * class is considerably simpler than {@code TcpIngestionRunner}.
 *
 * <p>{@code mode: CLIENT} is deliberately a no-op here (unlike TCP client mode, which still runs a
 * background reconnect loop) - REST client mode has no persistent connection/server concept at
 * all; it's purely on-demand via the Generic Publisher (see {@code PublisherService}/{@code
 * RestOperationInvoker}), which captures the HTTP response as a newly-observed message itself.
 */
@Component
public class RestIngestionRunner {
    private static final Logger log = LoggerFactory.getLogger(RestIngestionRunner.class);
    private static final int MAX_REQUEST_BODY_BYTES = 10 * 1024 * 1024;

    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final RestOperationRouter router;
    private final MessageIngestionPipeline pipeline;
    private final InterfaceRuntimeRegistry interfaceRuntimeRegistry;
    private final RestAutoReplySettingsService autoReplySettingsService;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, HttpServer> dedicatedHttpServers = new ConcurrentHashMap<>();

    public RestIngestionRunner(
            TrafficToolConfig trafficToolConfig,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            RestOperationRouter router,
            MessageIngestionPipeline pipeline,
            InterfaceRuntimeRegistry interfaceRuntimeRegistry,
            RestAutoReplySettingsService autoReplySettingsService,
            ObjectMapper objectMapper
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.restApiDefinitions = restApiDefinitions;
        this.router = router;
        this.pipeline = pipeline;
        this.interfaceRuntimeRegistry = interfaceRuntimeRegistry;
        this.autoReplySettingsService = autoReplySettingsService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void start() {
        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            if (interfaceConfig.isEnabled() && "REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                startInterface(interfaceConfig);
            }
        }
    }

    public synchronized void startInterface(InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();

        if ("CLIENT".equalsIgnoreCase(interfaceConfig.getMode())) {
            interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(false));
            return;
        }

        if (dedicatedHttpServers.containsKey(key)) {
            return;
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(interfaceConfig.getPort()), 0);
            server.createContext("/", exchange -> handleExchange(exchange, interfaceConfig));
            server.setExecutor(executor);
            server.start();
            dedicatedHttpServers.put(key, server);
            interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(true));
            log.info("REST ingestion started on port {} for interface {}", interfaceConfig.getPort(), interfaceConfig.getName());
        } catch (Exception e) {
            log.error("Failed to start REST ingestion on port {} for interface {}",
                    interfaceConfig.getPort(), interfaceConfig.getName(), e);
            throw new IllegalStateException(
                    "Failed to start interface " + interfaceConfig.getName() + ": " + e.getMessage(), e);
        }
    }

    public synchronized void stopInterface(String key) {
        HttpServer server = dedicatedHttpServers.remove(key);
        if (server != null) {
            server.stop(0);
        }
        interfaceRuntimeRegistry.state(key).ifPresent(state -> state.setListening(false));
    }

    private void handleExchange(HttpExchange exchange, InterfaceConfig interfaceConfig) {
        String key = interfaceConfig.getKey();

        try {
            String httpMethod = exchange.getRequestMethod();
            String requestPath = exchange.getRequestURI().getPath();
            RestApiDefinition api = restApiDefinitions.get(key);

            Optional<RestOperationRouter.RouteMatch> routeMatch =
                    api != null ? router.route(api, httpMethod, requestPath) : Optional.empty();

            if (routeMatch.isEmpty()) {
                writeResponse(exchange, 404, "{\"error\":\"No matching operation for " + httpMethod + " " + requestPath + "\"}");
                return;
            }

            RestOperationDefinition operation = routeMatch.get().operation();
            byte[] rawBody = readRequestBody(exchange.getRequestBody());
            String parseError = null;
            Map<String, Object> body = Map.of();

            if (rawBody.length > 0) {
                try {
                    body = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {
                    });
                } catch (Exception e) {
                    parseError = "Failed to parse JSON request body: " + e.getMessage();
                }
            }

            Map<String, Object> header = new LinkedHashMap<>(routeMatch.get().pathParams());
            header.putAll(queryParams(exchange));

            ObservedMessage message = pipeline.ingestRestOperation(
                    "REST",
                    exchange.getRemoteAddress().toString(),
                    interfaceConfig.getPort(),
                    interfaceConfig.getName(),
                    operation.operationId(),
                    header,
                    body,
                    rawBody,
                    parseError);

            interfaceRuntimeRegistry.state(key).ifPresent(state -> state.recordObserved(message.parseError() != null));

            log.info("Received REST {} {} on port {} for interface {} - operation={} - parseError={}",
                    httpMethod, requestPath, interfaceConfig.getPort(), interfaceConfig.getName(),
                    operation.operationId(), message.parseError());

            RestAutoReplySettingsService.ResolvedReply reply =
                    autoReplySettingsService.resolve(key, operation.operationId());
            writeResponse(exchange, reply.statusCode(), reply.body());
        } catch (Exception e) {
            log.warn("REST exchange handling failed for interface {}", key, e);
            try {
                writeResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            } catch (Exception ignored) {
                // best effort - the exchange is closed in the finally block regardless
            }
        } finally {
            exchange.close();
        }
    }

    private Map<String, String> queryParams(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }

        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 0) {
                params.put(pair, "");
            } else {
                params.put(pair.substring(0, separator), pair.substring(separator + 1));
            }
        }
        return params;
    }

    private byte[] readRequestBody(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_REQUEST_BODY_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BODY_BYTES) {
            throw new IOException("Request body exceeds maximum of " + MAX_REQUEST_BODY_BYTES + " bytes");
        }
        return bytes;
    }

    private void writeResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @PreDestroy
    public void stop() {
        for (HttpServer server : dedicatedHttpServers.values()) {
            server.stop(0);
        }
        dedicatedHttpServers.clear();
        executor.shutdownNow();
    }
}
