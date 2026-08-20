package com.example.monitor.publisher;

import com.example.monitor.ingestion.MessageIngestionPipeline;
import com.example.monitor.publishing.RestInvocationResult;
import com.example.monitor.publishing.RestOperationInvoker;
import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.TransportSelector;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestOperationDefinition;
import com.example.monitor.rest.RestParameterDefinition;
import com.example.monitor.rest.RestRequestBodyAssembler;
import com.example.monitor.schema.InterfaceConfig;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PublisherService {
    private final PublisherMetadataService metadataService;
    private final UdpMessagePublisher udpMessagePublisher;
    private final TcpMessagePublisher tcpMessagePublisher;
    private final Map<String, RestApiDefinition> restApiDefinitions;
    private final RestRequestBodyAssembler restRequestBodyAssembler;
    private final RestOperationInvoker restOperationInvoker;
    private final MessageIngestionPipeline messageIngestionPipeline;
    private final ObjectMapper objectMapper;

    public PublisherService(
            PublisherMetadataService metadataService,
            UdpMessagePublisher udpMessagePublisher,
            TcpMessagePublisher tcpMessagePublisher,
            @Qualifier("restApiDefinitions") Map<String, RestApiDefinition> restApiDefinitions,
            RestRequestBodyAssembler restRequestBodyAssembler,
            RestOperationInvoker restOperationInvoker,
            MessageIngestionPipeline messageIngestionPipeline,
            ObjectMapper objectMapper
    ) {
        this.metadataService = metadataService;
        this.udpMessagePublisher = udpMessagePublisher;
        this.tcpMessagePublisher = tcpMessagePublisher;
        this.restApiDefinitions = restApiDefinitions;
        this.restRequestBodyAssembler = restRequestBodyAssembler;
        this.restOperationInvoker = restOperationInvoker;
        this.messageIngestionPipeline = messageIngestionPipeline;
        this.objectMapper = objectMapper;
    }

    public PublisherSendResponse send(PublisherSendRequest request) {
        try {
            InterfaceConfig interfaceConfig = metadataService.requireInterfaceConfig(request.interfaceKey());

            if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                return sendRest(interfaceConfig, request);
            }

            MessageDefinitionRegistry registry = metadataService.registryFor(interfaceConfig);
            MessageDefinition definition = registry.find(interfaceConfig.getName(), request.messageType())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown message type: " + request.messageType()));

            byte[] payload = buildPayload(definition, interfaceConfig, request.fields());
            List<String> targets = new ArrayList<>();

            if (request.host() != null && !request.host().isBlank() && request.port() != null) {
                String transport = TransportSelector.normalize(request.transport());
                sendTo(transport, request.host(), request.port(), payload);
                targets.add(request.host() + ":" + request.port());
            }

            if (interfaceConfig.isShouldBroadcast()) {
                for (String target : interfaceConfig.getBroadcastTargets()) {
                    int separator = target.lastIndexOf(':');
                    String host = target.substring(0, separator);
                    int port = Integer.parseInt(target.substring(separator + 1));
                    sendTo("UDP", host, port, payload);
                    targets.add(target);
                }
            }

            if (targets.isEmpty()) {
                throw new IllegalArgumentException(
                        "No destination: provide host/port, or configure broadcastTargets for interface " + interfaceConfig.getKey());
            }

            return new PublisherSendResponse(true, payload.length, targets, null);
        } catch (Exception e) {
            return new PublisherSendResponse(false, 0, List.of(), e.getMessage());
        }
    }

    /**
     * Makes the actual outbound HTTP call for a REST client-mode (or ad-hoc) publish, then
     * captures the response as a newly-observed message ({@code messageType} suffixed
     * {@code " (response)"} so it reads distinctly from an inbound request in Live/History) -
     * this is REST client mode's whole point, unlike the fire-and-forget UDP/TCP path above.
     */
    private PublisherSendResponse sendRest(InterfaceConfig interfaceConfig, PublisherSendRequest request) throws Exception {
        RestApiDefinition api = restApiDefinitions.get(interfaceConfig.getKey());
        if (api == null) {
            return new PublisherSendResponse(false, 0, List.of(), "No REST API definition for interface: " + interfaceConfig.getKey());
        }

        RestOperationDefinition operation = api.findByOperationId(request.messageType())
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation: " + request.messageType()));

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
            return new PublisherSendResponse(false, 0, List.of(),
                    "No destination: provide host/port, or configure host/port on interface " + interfaceConfig.getKey());
        }

        RestInvocationResult result = restOperationInvoker.invoke(
                host, port, operation, pathParamValues, queryParamValues, jsonBody);

        if (result.parseError() != null) {
            return new PublisherSendResponse(false, 0, List.of(), result.parseError());
        }

        Map<String, Object> responseHeader = flattenHeaders(result.headers());
        responseHeader.put("statusCode", result.statusCode());

        messageIngestionPipeline.ingestRestOperation(
                "REST",
                host + ":" + port,
                port,
                interfaceConfig.getName(),
                operation.operationId() + " (response)",
                responseHeader,
                parseResponseBody(result.bodyBytes()),
                result.bodyBytes(),
                null);

        return new PublisherSendResponse(true, result.bodyBytes().length, List.of(host + ":" + port), null);
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

    private Map<String, Object> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, Object> flat = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                flat.put(entry.getKey(), entry.getValue().get(0));
            }
        }
        return flat;
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
     * Messages whose class parses/emits its own header (e.g. rada, {@link InterfaceConfig#isMessageOwnsHeader()})
     * only need {@code encodeBody}'s bytes as-is; everything else needs the opcode/timestamp
     * envelope wrapped around the body here.
     */
    private byte[] buildPayload(MessageDefinition definition, InterfaceConfig interfaceConfig, Map<String, Object> fields)
            throws Exception {
        byte[] body = definition.encodeBody(fields);

        if (interfaceConfig.isMessageOwnsHeader()) {
            return body;
        }

        return ProtocolHeaderCodec.encodeMessage(definition.opcode(), Instant.now().toEpochMilli(), body);
    }

    private void sendTo(String transport, String host, int port, byte[] payload) {
        if ("TCP".equals(transport)) {
            tcpMessagePublisher.send(host, port, payload);
        } else {
            udpMessagePublisher.send(host, port, payload);
        }
    }
}
