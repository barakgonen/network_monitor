package com.example.monitor.ingestion.rest;

import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.rest.RestAutoReplySettingsService;
import com.example.monitor.rest.RestOperationRouter;
import com.example.restschema.RestApiDefinition;
import com.example.restschema.RestOperationDefinition;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RestIngestionRunner} behavior the app-level RestInterfaceEndToEndIT
 * (traffic-monitor-app) / RestServerIngestionIT (this module's own IT) only exercise on the
 * happy path: unmatched routes, oversized bodies, malformed query strings, and CLIENT mode.
 */
@ExtendWith(MockitoExtension.class)
class RestIngestionRunnerTest {

    private static final String KEY = "restTest";

    @Mock
    private RestOperationRouter router;

    @Mock
    private MessageIngestionPipeline pipeline;

    @Mock
    private RestAutoReplySettingsService autoReplySettingsService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private RestIngestionRunner runner;
    private InterfaceConfig interfaceConfig;

    @BeforeEach
    void setUp() throws Exception {
        interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey(KEY);
        interfaceConfig.setName("REST Test Interface");
        interfaceConfig.setProtocol("REST");
        interfaceConfig.setPort(findFreePort());

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(interfaceConfig));

        RestApiDefinition api = new RestApiDefinition(KEY, List.of());

        runner = new RestIngestionRunner(
                trafficToolConfig,
                Map.of(KEY, api),
                router,
                pipeline,
                new InterfaceRuntimeRegistry(trafficToolConfig),
                autoReplySettingsService,
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        runner.stopInterface(KEY);
    }

    private static int findFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private URI uri(String pathAndQuery) {
        return URI.create("http://localhost:" + interfaceConfig.getPort() + pathAndQuery);
    }

    @Test
    void startInterface_clientMode_isNoOpAndDoesNotBindPort() {
        interfaceConfig.setMode("CLIENT");

        runner.startInterface(interfaceConfig);

        assertThatThrownBy(() -> new Socket("localhost", interfaceConfig.getPort()).close())
                .isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(pipeline);
    }

    @Test
    void handleExchange_noMatchingRoute_returns404() throws Exception {
        when(router.route(any(), any(), any())).thenReturn(Optional.empty());
        runner.startInterface(interfaceConfig);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/unknown")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        verifyNoInteractions(pipeline);
    }

    @Test
    void handleExchange_oversizedBody_returns500AndDoesNotIngest() throws Exception {
        RestOperationDefinition operation = new RestOperationDefinition(
                KEY, "createItem", "POST", "/items", List.of(), List.of(), List.of(), null, Map.of(), "summary", false);
        when(router.route(any(), eq("POST"), eq("/items")))
                .thenReturn(Optional.of(new RestOperationRouter.RouteMatch(operation, Map.of())));
        runner.startInterface(interfaceConfig);

        byte[] oversized = new byte[10 * 1024 * 1024 + 10];

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/items"))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(oversized))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(500);
        verifyNoInteractions(pipeline);
    }

    @Test
    void handleExchange_malformedQueryString_parsesTolerantlyAndMergesIntoHeader() throws Exception {
        RestOperationDefinition operation = new RestOperationDefinition(
                KEY, "listItems", "GET", "/items", List.of(), List.of(), List.of(), null, Map.of(), "summary", false);
        when(router.route(any(), eq("GET"), eq("/items")))
                .thenReturn(Optional.of(new RestOperationRouter.RouteMatch(operation, Map.of("existing", "pathValue"))));
        when(autoReplySettingsService.resolve(KEY, "listItems"))
                .thenReturn(new RestAutoReplySettingsService.ResolvedReply(200, "{}"));
        when(pipeline.ingestRestOperation(any(), any(), any(Integer.class), any(), any(), anyMap(), anyMap(), any(), any()))
                .thenReturn(observedMessage());

        runner.startInterface(interfaceConfig);

        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(uri("/items?a=1&flag&=orphanValue")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        verify(pipeline).ingestRestOperation(
                any(), any(), any(Integer.class), any(), any(), headerCaptor.capture(), anyMap(), any(), any());

        Map<String, Object> header = headerCaptor.getValue();
        assertThat(header)
                .containsEntry("existing", "pathValue")
                .containsEntry("a", "1")
                .containsEntry("flag", "")
                .containsEntry("", "orphanValue");
    }

    @Test
    void startInterface_portAlreadyBound_throwsIllegalStateException() throws Exception {
        try (ServerSocket occupied = new ServerSocket(interfaceConfig.getPort())) {
            assertThatThrownBy(() -> runner.startInterface(interfaceConfig))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private static ObservedMessage observedMessage() {
        return new ObservedMessage(
                "id", Instant.now(), "REST", "127.0.0.1:1", 1, "REST Test Interface",
                "listItems", Map.of(), Map.of(), 0, null, null, null);
    }
}
