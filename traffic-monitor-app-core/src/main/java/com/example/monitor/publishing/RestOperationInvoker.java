package com.example.monitor.publishing;

import com.example.monitor.rest.RestOperationDefinition;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Makes the actual HTTP call for REST client-mode publishing, using the JDK's built-in {@link
 * HttpClient} (no new dependency). Unlike {@link UdpMessagePublisher}/{@link TcpMessagePublisher}
 * (send-and-forget), REST client mode's whole point is the response - {@link
 * com.example.monitor.publisher.PublisherService} captures it as a newly-observed message via
 * {@code MessageIngestionPipeline.ingestRestOperation}. {@code http://} only in v1 - no HTTPS
 * config surface.
 */
@Component
public class RestOperationInvoker {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    public RestOperationInvoker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RestInvocationResult invoke(
            String host,
            int port,
            RestOperationDefinition operation,
            Map<String, String> pathParamValues,
            Map<String, String> queryParamValues,
            Map<String, Object> jsonBody
    ) {
        try {
            URI uri = buildUri(host, port, operation.pathTemplate(), pathParamValues, queryParamValues);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri);

            HttpRequest.BodyPublisher bodyPublisher;
            if (jsonBody != null && !jsonBody.isEmpty()) {
                bodyPublisher = HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(jsonBody));
                requestBuilder.header("Content-Type", "application/json");
            } else {
                bodyPublisher = HttpRequest.BodyPublishers.noBody();
            }
            requestBuilder.method(operation.httpMethod(), bodyPublisher);

            HttpResponse<byte[]> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());
            return new RestInvocationResult(response.statusCode(), response.headers().map(), response.body(), null);
        } catch (Exception e) {
            // Network-level exceptions (e.g. ConnectException for "connection refused") commonly
            // have a null getMessage() - falling back to toString() ensures parseError is always
            // non-null when an exception was actually caught, so callers (PublisherService)
            // never mistake a failed call for a successful one with an empty response.
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            return new RestInvocationResult(0, Map.of(), new byte[0], message);
        }
    }

    /**
     * Path parameter values are substituted as-is (no URL-encoding) - v1 assumes simple values
     * (ids, slugs) without characters that would need escaping in a path segment; query values
     * are encoded, since those commonly do carry spaces/special characters.
     */
    private URI buildUri(
            String host, int port, String pathTemplate,
            Map<String, String> pathParamValues, Map<String, String> queryParamValues) {
        String path = pathTemplate;
        for (Map.Entry<String, String> entry : pathParamValues.entrySet()) {
            path = path.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : queryParamValues.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }

        String uriString = "http://" + host + ":" + port + path + (query.isEmpty() ? "" : "?" + query);
        return URI.create(uriString);
    }
}
