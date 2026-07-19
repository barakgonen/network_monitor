package com.example.monitor.publisher;

import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.TransportSelector;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.example.monitor.schema.InterfaceConfig;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeaderCodec;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PublisherService {
    private final PublisherMetadataService metadataService;
    private final UdpMessagePublisher udpMessagePublisher;
    private final TcpMessagePublisher tcpMessagePublisher;

    public PublisherService(
            PublisherMetadataService metadataService,
            UdpMessagePublisher udpMessagePublisher,
            TcpMessagePublisher tcpMessagePublisher
    ) {
        this.metadataService = metadataService;
        this.udpMessagePublisher = udpMessagePublisher;
        this.tcpMessagePublisher = tcpMessagePublisher;
    }

    public PublisherSendResponse send(PublisherSendRequest request) {
        try {
            InterfaceConfig interfaceConfig = metadataService.requireInterfaceConfig(request.interfaceKey());
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
     * Dedicated-port messages (e.g. rada) embed their own header as part of {@code encodeBody}, so
     * only the legacy shared-envelope interfaces need the opcode/timestamp wrap applied here.
     */
    private byte[] buildPayload(MessageDefinition definition, InterfaceConfig interfaceConfig, Map<String, Object> fields)
            throws Exception {
        byte[] body = definition.encodeBody(fields);

        if (interfaceConfig.hasDedicatedPort()) {
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
