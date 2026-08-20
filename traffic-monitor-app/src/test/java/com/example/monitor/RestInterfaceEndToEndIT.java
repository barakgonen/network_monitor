package com.example.monitor;

import com.example.monitor.interfaces.InterfaceStatusDto;
import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real end-to-end coverage of the {@code pets} REST interface running inside the full real app
 * (real {@code com.example.schemas}/{@code com.example.messagehandlers} on the classpath,
 * alongside every other real interface) - distinct from {@code RestServerIngestionIT}/
 * {@code RestClientPublishingIT} in traffic-monitor-app-core, which boot a minimal REST-only test
 * app. This proves the actual demo {@code swagger/pets-demo.yml} config works when wired through
 * the real app, and that REST genuinely coexists with UDP/TCP interfaces in the same instance.
 */
class RestInterfaceEndToEndIT extends AbstractIntegrationTestBase {

    private static final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stopPetsInterface() {
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/stop"), null, InterfaceStatusDto[].class);
    }

    @Test
    void startingInterface_thenGettingPet_landsInStoreAndReportsListening() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/start"), null, InterfaceStatusDto[].class);

        InterfaceStatusDto[] statuses =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();
        assertThat(statuses).extracting(InterfaceStatusDto::key).contains("pets");
        assertThat(statuses).filteredOn(dto -> "pets".equals(dto.key())).allMatch(InterfaceStatusDto::listening);

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + petsPort + "/pets/7")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"name\":\"Rex\"");

        ObservedMessage message = awaitStoreContains(m -> "getPet".equals(m.messageType()));
        assertThat(message.interfaceName()).isEqualTo("Pets REST Interface");
        assertThat(message.transportProtocol()).isEqualTo("REST");
        assertThat(message.header()).containsEntry("petId", "7");
        assertThat(message.parseError()).isNull();

        InterfaceStatusDto[] afterMessage =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();
        assertThat(afterMessage).filteredOn(dto -> "pets".equals(dto.key())).allMatch(dto -> dto.receivedCount() >= 1);
    }

    @Test
    void creatingPet_withNestedAndArrayFields_decodesRealDemoSchemaCorrectly() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/start"), null, InterfaceStatusDto[].class);

        String body = """
                {"name":"Fido","species":"dog","age":4,
                 "owner":{"name":"Alice","email":"alice@example.com"},
                 "tags":["loud","good boy"],
                 "vaccinations":[{"vaccine":"Rabies","administeredOn":"2024-01-01"}]}
                """;

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + petsPort + "/pets"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(201);

        ObservedMessage message = awaitStoreContains(m -> "createPet".equals(m.messageType()));
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("name")).isEqualTo("Fido");

        @SuppressWarnings("unchecked")
        var owner = (java.util.Map<String, Object>) message.body().get("owner");
        assertThat(owner).containsEntry("name", "Alice");

        @SuppressWarnings("unchecked")
        var tags = (java.util.List<Object>) message.body().get("tags");
        assertThat(tags).containsExactly("loud", "good boy");
    }

    @Test
    void restInterface_coexistsWithRealUdpInterface_inTheSameRunningApp() throws Exception {
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/start"), null, InterfaceStatusDto[].class);

        // A real UDP interface (Fruit, backed by real com.example.schemas classes) sent while the
        // REST interface is also live - proves the two ingestion paths don't interfere.
        sendUdp(fruitPort, TestProtocolPayloads.orange("Coexistence Farm", (byte) 1));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + petsPort + "/pets/1")).GET().build();
        client.send(request, HttpResponse.BodyHandlers.ofString());

        ObservedMessage fruitMessage = awaitStoreContains(
                m -> "Orange".equals(m.messageType()) && "Coexistence Farm".equals(m.body().get("sourceFarm")));
        ObservedMessage petMessage = awaitStoreContains(m -> "getPet".equals(m.messageType()));

        assertThat(fruitMessage.transportProtocol()).isEqualTo("UDP");
        assertThat(petMessage.transportProtocol()).isEqualTo("REST");
    }

    @Test
    void stoppingInterface_reportsNotListening() {
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/start"), null, InterfaceStatusDto[].class);
        restTemplate.postForEntity(httpUrl("/api/interfaces/pets/stop"), null, InterfaceStatusDto[].class);

        InterfaceStatusDto[] statuses =
                restTemplate.getForEntity(httpUrl("/api/interfaces"), InterfaceStatusDto[].class).getBody();

        assertThat(statuses).filteredOn(dto -> "pets".equals(dto.key())).allMatch(dto -> !dto.listening());
    }
}
