package com.example.monitor.publisher;

import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.model.ObservedMessage;
import com.example.monitor.publishing.RestInvocationResult;
import com.example.monitor.publishing.RestOperationInvoker;
import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestParameterDefinition;
import com.example.monitor.rest.RestRequestBodyAssembler;
import com.example.monitor.rest.RestSchemaNode;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublisherService}'s REST branch ({@code sendRest}) - previously only
 * exercised indirectly via {@code RestClientPublishingIT}'s real HTTP round trip. These mock the
 * I/O boundary ({@link RestOperationInvoker}/{@link MessageIngestionPipeline}) to isolate the
 * param-extraction/body-assembly/response-capture logic itself.
 */
@ExtendWith(MockitoExtension.class)
class PublisherServiceRestTest {

    @Mock
    private UdpMessagePublisher udpMessagePublisher;

    @Mock
    private TcpMessagePublisher tcpMessagePublisher;

    @Mock
    private RestOperationInvoker restOperationInvoker;

    @Mock
    private MessageIngestionPipeline messageIngestionPipeline;

    private InterfaceConfig ordersConfig;
    private RestOperationDefinition updateOrderOperation;
    private PublisherService service;

    @BeforeEach
    void setUp() {
        ordersConfig = new InterfaceConfig();
        ordersConfig.setKey("orders");
        ordersConfig.setName("Orders REST Interface");
        ordersConfig.setProtocol("REST");
        ordersConfig.setPort(5061);

        RestSchemaNode bodySchema = new RestSchemaNode("", "object", null,
                List.of(new RestSchemaNode("note", "string", null, null, null, null, false, null)),
                null, null, false, null);

        updateOrderOperation = new RestOperationDefinition(
                "orders", "updateOrder", "PUT", "/orders/{orderId}",
                List.of(new RestParameterDefinition("orderId", "path", null, true)),
                List.of(new RestParameterDefinition("verbose", "query", null, false)),
                List.of(),
                bodySchema, Map.of(), "Update an order", true);

        RestApiDefinition ordersApi = new RestApiDefinition("orders", List.of(updateOrderOperation));

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(ordersConfig));

        PublisherMetadataService metadataService = new PublisherMetadataService(trafficToolConfig, Map.of());

        service = new PublisherService(
                metadataService, udpMessagePublisher, tcpMessagePublisher,
                Map.of("orders", ordersApi), new RestRequestBodyAssembler(),
                restOperationInvoker, messageIngestionPipeline, new ObjectMapper());
    }

    @Test
    void send_extractsPathAndQueryParams_assemblesBody_andCapturesResponseAsObservedMessage() {
        Map<String, Object> fields = Map.of("orderId", "42", "verbose", "true", "note", "shipped");
        PublisherSendRequest request = new PublisherSendRequest("orders", "updateOrder", "orders.example.com", 8080, "REST", fields);

        byte[] responseBytes = "{\"status\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
        when(restOperationInvoker.invoke(
                eq("orders.example.com"), eq(8080), eq(updateOrderOperation),
                eq(Map.of("orderId", "42")), eq(Map.of("verbose", "true")), eq(Map.of("note", "shipped"))))
                .thenReturn(new RestInvocationResult(200, Map.of("Content-Type", List.of("application/json")), responseBytes, null));
        when(messageIngestionPipeline.ingestRestOperation(
                anyString(), anyString(), anyInt(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mockObservedMessage());

        PublisherSendResponse response = service.send(request);

        assertThat(response.success()).isTrue();
        assertThat(response.bytesSent()).isEqualTo(responseBytes.length);
        assertThat(response.targets()).containsExactly("orders.example.com:8080");

        ArgumentCaptor<Map<String, Object>> headerCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);

        verify(messageIngestionPipeline).ingestRestOperation(
                eq("REST"), eq("orders.example.com:8080"), eq(8080), eq("Orders REST Interface"),
                eq("updateOrder (response)"), headerCaptor.capture(), bodyCaptor.capture(), eq(responseBytes), eq(null));

        assertThat(headerCaptor.getValue()).containsEntry("Content-Type", "application/json").containsEntry("statusCode", 200);
        assertThat(bodyCaptor.getValue()).containsEntry("status", "ok");
    }

    @Test
    void send_withUnknownOperation_returnsFailureResponse_withoutInvokingHttpClient() {
        PublisherSendResponse response = service.send(
                new PublisherSendRequest("orders", "deleteOrder", "host", 8080, "REST", Map.of()));

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("Unknown operation");
        verifyNoInteractions(restOperationInvoker, messageIngestionPipeline);
    }

    @Test
    void send_withNoDestinationConfigured_returnsFailureResponse() {
        // No host/port in the request, and none configured on the interface either.
        PublisherSendResponse response = service.send(
                new PublisherSendRequest("orders", "updateOrder", null, null, "REST", Map.of("orderId", "1")));

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("No destination");
        verifyNoInteractions(restOperationInvoker, messageIngestionPipeline);
    }

    @Test
    void send_fallsBackToInterfaceConfiguredHostAndPort_whenRequestOmitsThem() {
        ordersConfig.setHost("configured-host");
        ordersConfig.setMode("CLIENT");

        when(restOperationInvoker.invoke(eq("configured-host"), eq(5061), any(), any(), any(), any()))
                .thenReturn(new RestInvocationResult(200, Map.of(), new byte[0], null));
        when(messageIngestionPipeline.ingestRestOperation(
                anyString(), anyString(), anyInt(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(mockObservedMessage());

        PublisherSendResponse response = service.send(
                new PublisherSendRequest("orders", "updateOrder", null, null, "REST", Map.of("orderId", "1")));

        assertThat(response.success()).isTrue();
        assertThat(response.targets()).containsExactly("configured-host:5061");
    }

    @Test
    void send_whenInvokerReportsParseError_returnsFailureResponse_andNeverIngestsResponseMessage() {
        when(restOperationInvoker.invoke(any(), anyInt(), any(), any(), any(), any()))
                .thenReturn(new RestInvocationResult(0, Map.of(), new byte[0], "Connection refused"));

        PublisherSendResponse response = service.send(
                new PublisherSendRequest("orders", "updateOrder", "host", 8080, "REST", Map.of("orderId", "1")));

        assertThat(response.success()).isFalse();
        assertThat(response.error()).isEqualTo("Connection refused");
        verifyNoInteractions(messageIngestionPipeline);
    }

    private ObservedMessage mockObservedMessage() {
        return new ObservedMessage(
                "id", java.time.Instant.now(), "REST", "remote", 0, "Orders REST Interface",
                "updateOrder (response)", Map.of(), Map.of(), 0, "", "", null);
    }
}
