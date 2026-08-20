package com.example.tester.rest;

import com.example.tester.config.PayloadConfig;
import com.example.tester.config.PetsPayloadConfig;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sends one JSON-over-HTTP request against a {@code protocol: REST} interface hosted by
 * traffic-monitor-app (server mode) - the REST analogue of {@code UdpPublisher}/{@code
 * TcpPublisher}, but a request/response call rather than a fire-and-forget send, since that's
 * how HTTP works. Only {@link com.example.tester.config.PayloadMode#PETS_CREATE}/{@code
 * PETS_GET} are supported today, matching the two operations swagger/pets-demo.yml defines - the
 * HTTP method itself is independently overridable via {@link PetsPayloadConfig#getMethod()} (e.g.
 * to exercise a method the swagger spec doesn't define for that path and confirm the monitor
 * 404s it, rather than always sending whatever's natural for the mode).
 */
public class RestPublisher {
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RestSendResult send(String host, int port, PayloadConfig config) throws IOException, InterruptedException {
        return switch (config.getMode()) {
            case PETS_CREATE -> createPet(host, port, config.getPets());
            case PETS_GET -> getPet(host, port, config.getPets());
            default -> throw new IllegalArgumentException("Not a REST payload mode: " + config.getMode());
        };
    }

    private RestSendResult createPet(String host, int port, PetsPayloadConfig pets) throws IOException, InterruptedException {
        Map<String, Object> owner = new LinkedHashMap<>();
        owner.put("name", pets.getOwnerName());
        owner.put("email", pets.getOwnerEmail());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", pets.getName());
        body.put("species", pets.getSpecies());
        body.put("age", pets.getAge());
        body.put("owner", owner);
        body.put("tags", pets.getTags());

        byte[] bytes = objectMapper.writeValueAsBytes(body);
        String method = resolveMethod(pets, "POST");

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/pets"))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build();

        return send(method, request);
    }

    private RestSendResult getPet(String host, int port, PetsPayloadConfig pets) throws IOException, InterruptedException {
        String petId = URLEncoder.encode(pets.getPetId(), StandardCharsets.UTF_8);
        String query = pets.isIncludeVaccinations() ? "?includeVaccinations=true" : "";
        String method = resolveMethod(pets, "GET");

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://" + host + ":" + port + "/pets/" + petId + query))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .build();

        return send(method, request);
    }

    /** Null/blank {@link PetsPayloadConfig#getMethod()} means "use whatever's natural for the mode". */
    private String resolveMethod(PetsPayloadConfig pets, String defaultMethod) {
        String method = pets.getMethod();
        return method != null && !method.isBlank() ? method.trim().toUpperCase(Locale.ROOT) : defaultMethod;
    }

    private RestSendResult send(String method, HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new RestSendResult(method, response.statusCode(), response.body());
    }
}
