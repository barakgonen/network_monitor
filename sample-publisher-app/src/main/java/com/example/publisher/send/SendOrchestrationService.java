package com.example.publisher.send;

import com.example.monitor.publishing.RestInvocationResult;
import com.example.monitor.publishing.RestOperationInvoker;
import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.TransportSelector;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestParameterDefinition;
import com.example.monitor.schema.InterfaceConfig;
import com.example.publisher.metadata.PublishableInterfaceService;
import com.example.publisher.rest.RestRequestBodyAssembler;
import com.example.publisher.serdes.SerdesRequestBodyAssembler;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.binaryserdes.MessageType;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This app's {@code PublisherService} equivalent - resolves a {@link SendRequest} to either a
 * serdes-encoded UDP/TCP payload or a REST call, and sends it. Unlike the deleted
 * {@code com.example.monitor.publisher.PublisherService}, there is no ingestion side effect - a
 * REST response is returned directly in the {@link SendResult}, not captured as an "observed
 * message" (this app doesn't store anything).
 */
@Component
public class SendOrchestrationService {

    private final PublishableInterfaceService interfaceService;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;
    private final Map<String, List<MessageType>> interfaceMessageTypes;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final SerdesRequestBodyAssembler serdesRequestBodyAssembler;
    private final RestRequestBodyAssembler restRequestBodyAssembler;
    private final UdpMessagePublisher udpMessagePublisher;
    private final TcpMessagePublisher tcpMessagePublisher;
    private final RestOperationInvoker restOperationInvoker;
    private final ObjectMapper objectMapper;

    public SendOrchestrationService(
            PublishableInterfaceService interfaceService,
            @Qualifier("interfaceMessageDefinitionRegistries") Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries,
            Map<String, List<MessageType>> interfaceMessageTypes,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            SerdesRequestBodyAssembler serdesRequestBodyAssembler,
            RestRequestBodyAssembler restRequestBodyAssembler,
            UdpMessagePublisher udpMessagePublisher,
            TcpMessagePublisher tcpMessagePublisher,
            RestOperationInvoker restOperationInvoker,
            ObjectMapper objectMapper
    ) {
        this.interfaceService = interfaceService;
        this.interfaceMessageDefinitionRegistries = interfaceMessageDefinitionRegistries;
        this.interfaceMessageTypes = interfaceMessageTypes;
        this.restApiDefinitions = restApiDefinitions;
        this.serdesRequestBodyAssembler = serdesRequestBodyAssembler;
        this.restRequestBodyAssembler = restRequestBodyAssembler;
        this.udpMessagePublisher = udpMessagePublisher;
        this.tcpMessagePublisher = tcpMessagePublisher;
        this.restOperationInvoker = restOperationInvoker;
        this.objectMapper = objectMapper;
    }

    public SendResult send(SendRequest request) {
        try {
            InterfaceConfig interfaceConfig = interfaceService.requireInterfaceConfig(request.interfaceKey());

            if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                return sendRest(interfaceConfig, request);
            }

            return sendSerdes(interfaceConfig, request);
        } catch (Exception e) {
            return SendResult.failure(e.getMessage());
        }
    }

    private SendResult sendSerdes(InterfaceConfig interfaceConfig, SendRequest request) throws Exception {
        MessageDefinitionRegistry registry = interfaceMessageDefinitionRegistries.get(interfaceConfig.getKey());
        MessageDefinition definition = registry.find(interfaceConfig.getName(), request.messageId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + request.messageId()));
        MessageType messageType = findMessageType(interfaceConfig.getKey(), request.messageId());

        Map<String, Object> fields = serdesRequestBodyAssembler.assemble(
                messageType, request.fields() != null ? request.fields() : Map.of());
        byte[] body = definition.encodeBody(fields);

        // messageOwnsHeader interfaces (e.g. rada) already include header fields in their own
        // field list - the body IS the full wire payload. Everything else needs the legacy
        // opcode+timestamp+bodyLength envelope wrapped around it.
        byte[] payload = interfaceConfig.isMessageOwnsHeader()
                ? body
                : ProtocolHeaderCodec.encodeMessage(definition.opcode(), Instant.now().toEpochMilli(), body);

        String host = request.host() != null && !request.host().isBlank() ? request.host() : "localhost";
        Integer port = request.port() != null ? request.port() : interfaceConfig.getPort();
        if (port == null) {
            return SendResult.failure("No destination: provide a port, or configure one on interface " + interfaceConfig.getKey());
        }

        String transport = request.transport() != null && !request.transport().isBlank()
                ? TransportSelector.normalize(request.transport())
                : TransportSelector.normalize(interfaceConfig.getProtocol());

        if ("TCP".equals(transport)) {
            tcpMessagePublisher.send(host, port, payload);
        } else {
            udpMessagePublisher.send(host, port, payload);
        }

        return SendResult.sent(payload.length, List.of(host + ":" + port));
    }

    private SendResult sendRest(InterfaceConfig interfaceConfig, SendRequest request) throws Exception {
        RestApiDefinition api = restApiDefinitions.get(interfaceConfig.getKey());
        if (api == null) {
            return SendResult.failure("No REST API definition for interface: " + interfaceConfig.getKey());
        }

        RestOperationDefinition operation = api.findByOperationId(request.messageId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + request.messageId()));

        Map<String, Object> fields = request.fields() != null ? request.fields() : Map.of();
        Map<String, String> pathParamValues = extractParamValues(operation.pathParameters(), fields);
        Map<String, String> queryParamValues = extractParamValues(operation.queryParameters(), fields);

        Map<String, Object> bodyFields = new LinkedHashMap<>(fields);
        operation.pathParameters().forEach(p -> bodyFields.remove(p.name()));
        operation.queryParameters().forEach(p -> bodyFields.remove(p.name()));

        Map<String, Object> jsonBody = operation.requestBodySchema() != null
                ? restRequestBodyAssembler.assemble(operation.requestBodySchema(), bodyFields)
                : Map.of();

        String host = request.host() != null && !request.host().isBlank() ? request.host() : interfaceConfig.getHost();
        Integer port = request.port() != null ? request.port() : interfaceConfig.getPort();

        if (host == null || host.isBlank() || port == null) {
            return SendResult.failure("No destination: provide host/port for interface " + interfaceConfig.getKey());
        }

        RestInvocationResult result = restOperationInvoker.invoke(
                host, port, operation, pathParamValues, queryParamValues, jsonBody);

        if (result.parseError() != null) {
            return SendResult.failure(result.parseError());
        }

        return SendResult.restResponse(result.statusCode(), List.of(host + ":" + port), parseResponseBody(result.bodyBytes()));
    }

    private Map<String, String> extractParamValues(List<RestParameterDefinition> parameters, Map<String, Object> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        for (RestParameterDefinition parameter : parameters) {
            Object value = fields.get(parameter.name());
            if (value != null) {
                values.put(parameter.name(), String.valueOf(value));
            }
        }
        return values;
    }

    private Map<String, Object> parseResponseBody(byte[] bytes) {
        if (bytes.length == 0) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(bytes, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return Map.of("raw", new String(bytes, StandardCharsets.UTF_8));
        }
    }

    private MessageType findMessageType(String interfaceKey, String messageId) {
        return interfaceMessageTypes.getOrDefault(interfaceKey, List.of()).stream()
                .filter(mt -> mt.getName().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + messageId));
    }
}
