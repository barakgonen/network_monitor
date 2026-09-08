package com.example.publisher.send;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.ProtocolOut;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.example.publisher.metadata.PublishableInterfaceService;
import com.example.publisher.rest.RestRequestBodyAssembler;
import com.example.publisher.send.io.RestInvocationResult;
import com.example.publisher.send.io.RestOperationInvoker;
import com.example.publisher.send.io.TcpMessagePublisher;
import com.example.publisher.send.io.TransportSelector;
import com.example.publisher.send.io.UdpMessagePublisher;
import com.example.publisher.serdes.SerdesRequestBodyAssembler;
import com.example.restschema.RestApiDefinition;
import com.example.restschema.RestOperationDefinition;
import com.example.restschema.RestParameterDefinition;
import com.example.trafficconfig.InterfaceConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This app's {@code PublisherService} equivalent - resolves a {@link SendRequest} to either a
 * serdes-encoded UDP/TCP payload or a REST call, and sends it. Unlike the deleted
 * {@code com.example.monitor.publisher.PublisherService}, there is no ingestion side effect - a
 * REST response is returned directly in the {@link SendResult}, not captured as an "observed
 * message" (this app doesn't store anything).
 *
 * <p>{@code sendSerdes} deliberately bypasses the {@code MessageDefinition}/
 * {@code MessageDefinitionRegistry} abstraction traffic-monitor-app-core's ingestion pipeline
 * uses - this app has no access to it (those interfaces, and their
 * {@code SerdesMessageDefinition} adapter, stayed behind in traffic-monitor-app-core as
 * ingestion-only concerns) and no reason to: it already holds the parsed {@link MessageType} for
 * field description, so it encodes directly via {@link ProtocolOut#encode(String, String, java.nio.ByteOrder)}
 * instead, the same API {@code SerdesMessageDefinition.encodeBody} wraps one layer up.
 */
@Component
public class SendOrchestrationService {

    // Classic Jackson 2, matching what the binary-serdes engine's ProtocolOut/ProtocolIn use
    // internally - Spring only autoconfigures a Jackson 3 (tools.jackson.*) ObjectMapper bean, so
    // this one is a plain static instance, same as SerdesMessageDefinition.encodeBody does.
    private static final com.fasterxml.jackson.databind.ObjectMapper SERDES_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final PublishableInterfaceService interfaceService;
    private final Map<String, List<MessageType>> interfaceMessageTypes;
    private final Map<String, ProtocolOut> interfaceProtocolOuts;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final SerdesRequestBodyAssembler serdesRequestBodyAssembler;
    private final RestRequestBodyAssembler restRequestBodyAssembler;
    private final UdpMessagePublisher udpMessagePublisher;
    private final TcpMessagePublisher tcpMessagePublisher;
    private final RestOperationInvoker restOperationInvoker;
    private final ObjectMapper objectMapper;

    public SendOrchestrationService(
            PublishableInterfaceService interfaceService,
            Map<String, List<MessageType>> interfaceMessageTypes,
            Map<String, ProtocolOut> interfaceProtocolOuts,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            SerdesRequestBodyAssembler serdesRequestBodyAssembler,
            RestRequestBodyAssembler restRequestBodyAssembler,
            UdpMessagePublisher udpMessagePublisher,
            TcpMessagePublisher tcpMessagePublisher,
            RestOperationInvoker restOperationInvoker,
            ObjectMapper objectMapper
    ) {
        this.interfaceService = interfaceService;
        this.interfaceMessageTypes = interfaceMessageTypes;
        this.interfaceProtocolOuts = interfaceProtocolOuts;
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
        MessageType messageType = findMessageType(interfaceConfig.getKey(), request.messageId());
        ProtocolOut protocolOut = interfaceProtocolOuts.get(interfaceConfig.getKey());

        Map<String, Object> fields = serdesRequestBodyAssembler.assemble(
                messageType, request.fields() != null ? request.fields() : Map.of());
        String json = SERDES_MAPPER.writeValueAsString(fields);
        byte[] body = encodeSerdesBody(protocolOut, messageType.getName(), json, interfaceConfig.resolveByteOrder());

        // messageOwnsHeader interfaces (e.g. rada) already include header fields in their own
        // field list - the body IS the full wire payload. Everything else needs the legacy
        // opcode+timestamp+bodyLength envelope wrapped around it. The legacy envelope's opcode is
        // intentionally still a 4-byte int (see CLAUDE.md), so this narrowing cast is safe: it's
        // only reached for legacy-envelope interfaces, whose opcodes are always in int range.
        byte[] payload = interfaceConfig.isMessageOwnsHeader()
                ? body
                : ProtocolHeaderCodec.encodeMessage((int) messageType.getOpcode(), Instant.now().toEpochMilli(), body);

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

    /**
     * {@link ProtocolOut#encode(String, String, ByteOrder)} pre-sizes its buffer by summing every
     * field's fixed {@code sizeInBytes} and throws if any field is variable-length (e.g. a
     * {@code string}) - it has no way to know the encoded size of a variable field before writing
     * it. Every legacy envelope protocol this app sends for (fruit/weather/candy/greeting) has at
     * least one string field, so this over-allocates a buffer generously instead and trims to the
     * bytes {@link ProtocolOut#encodeInto(String, String, ByteBuffer)} actually wrote - identical
     * to what {@code SerdesMessageDefinition.encodeBody} does in traffic-monitor-app-core, since
     * this app has no access to that ingestion-only class.
     */
    private byte[] encodeSerdesBody(ProtocolOut protocolOut, String messageName, String json, ByteOrder byteOrder) throws Exception {
        int bufferSize = json.getBytes(StandardCharsets.UTF_8).length + 1024;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
        protocolOut.encodeInto(messageName, json, buffer);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private MessageType findMessageType(String interfaceKey, String messageId) {
        return interfaceMessageTypes.getOrDefault(interfaceKey, List.of()).stream()
                .filter(mt -> mt.getName().equals(messageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + messageId));
    }
}
