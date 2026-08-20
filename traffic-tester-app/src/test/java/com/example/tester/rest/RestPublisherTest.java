package com.example.tester.rest;

import com.example.tester.config.PayloadConfig;
import com.example.tester.config.PayloadMode;
import com.example.tester.config.PetsPayloadConfig;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RestPublisherTest {

    private final RestPublisher publisher = new RestPublisher();
    private HttpServer stubServer;
    private int stubPort;

    private final AtomicReference<String> capturedMethod = new AtomicReference<>();
    private final AtomicReference<String> capturedPathAndQuery = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();

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
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] responseBytes = "{\"name\":\"Rex\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, responseBytes.length);
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
    void send_withPetsCreateMode_postsJsonBody() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setName("Fluffy");
        pets.setSpecies("cat");
        pets.setAge(2);
        pets.setOwnerName("Bob");
        pets.setOwnerEmail("bob@example.com");
        pets.setTags(List.of("fluffy", "calm"));

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_CREATE);
        config.setPets(pets);

        RestSendResult result = publisher.send("localhost", stubPort, config);

        assertThat(capturedMethod.get()).isEqualTo("POST");
        assertThat(capturedPathAndQuery.get()).isEqualTo("/pets");
        assertThat(capturedBody.get())
                .contains("\"name\":\"Fluffy\"")
                .contains("\"species\":\"cat\"")
                .contains("\"age\":2")
                .contains("\"owner\":{\"name\":\"Bob\",\"email\":\"bob@example.com\"}")
                .contains("\"tags\":[\"fluffy\",\"calm\"]");

        assertThat(result.statusCode()).isEqualTo(201);
        assertThat(result.body()).isEqualTo("{\"name\":\"Rex\"}");
        assertThat(result.method()).isEqualTo("POST");
    }

    @Test
    void send_withPetsGetMode_getsWithPetIdInPathAndQueryParam() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setPetId("42");
        pets.setIncludeVaccinations(true);

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_GET);
        config.setPets(pets);

        RestSendResult result = publisher.send("localhost", stubPort, config);

        assertThat(capturedMethod.get()).isEqualTo("GET");
        assertThat(capturedPathAndQuery.get()).isEqualTo("/pets/42?includeVaccinations=true");
        assertThat(capturedBody.get()).isEmpty();
        assertThat(result.method()).isEqualTo("GET");
    }

    @Test
    void send_withPetsGetModeAndVaccinationsFalse_omitsQueryParam() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setPetId("7");
        pets.setIncludeVaccinations(false);

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_GET);
        config.setPets(pets);

        publisher.send("localhost", stubPort, config);

        assertThat(capturedPathAndQuery.get()).isEqualTo("/pets/7");
    }

    @Test
    void send_withExplicitMethodOverride_onGetMode_usesOverriddenMethodInsteadOfDefault() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setPetId("7");
        pets.setMethod("DELETE");

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_GET);
        config.setPets(pets);

        RestSendResult result = publisher.send("localhost", stubPort, config);

        assertThat(capturedMethod.get()).isEqualTo("DELETE");
        assertThat(result.method()).isEqualTo("DELETE");
    }

    @Test
    void send_withExplicitMethodOverride_onCreateMode_usesOverriddenMethodButStillSendsBody() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setName("Fluffy");
        pets.setMethod("put");

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_CREATE);
        config.setPets(pets);

        RestSendResult result = publisher.send("localhost", stubPort, config);

        // Method strings are normalized to uppercase regardless of how they're cased in config.
        assertThat(capturedMethod.get()).isEqualTo("PUT");
        assertThat(result.method()).isEqualTo("PUT");
        assertThat(capturedBody.get()).contains("\"name\":\"Fluffy\"");
    }

    @Test
    void send_withBlankMethodOverride_fallsBackToModeDefault() throws Exception {
        PetsPayloadConfig pets = new PetsPayloadConfig();
        pets.setPetId("1");
        pets.setMethod("  ");

        PayloadConfig config = new PayloadConfig();
        config.setMode(PayloadMode.PETS_GET);
        config.setPets(pets);

        publisher.send("localhost", stubPort, config);

        assertThat(capturedMethod.get()).isEqualTo("GET");
    }
}
