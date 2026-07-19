package com.example.monitor.publisher;

import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TransportProtocol;

import com.example.monitor.api.publisher.PublisherSendRequest;
import com.example.monitor.api.publisher.PublisherSendResponse;
import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PublisherService {
    private final PublisherMetadataService metadataService;
    private final ReflectionFieldApplier fieldApplier;
    private final ReflectionMessageSerializer serializer;
    private final TrafficPublisher trafficPublisher;

    public PublisherService(
            PublisherMetadataService metadataService,
            ReflectionFieldApplier fieldApplier,
            ReflectionMessageSerializer serializer,
            TrafficPublisher trafficPublisher
    ) {
        this.metadataService = metadataService;
        this.fieldApplier = fieldApplier;
        this.serializer = serializer;
        this.trafficPublisher = trafficPublisher;
    }

    public PublisherSendResponse send(PublisherSendRequest request) {
        try {
            TrafficMonitorProperties.ReflectionInterface reflectionInterface =
                    metadataService.requireInterface(requireInterfaceName(request));

            validate(request, reflectionInterface);

            TrafficMonitorProperties.SupportedMessage supportedMessage =
                    metadataService.requireMessage(reflectionInterface, request.opcode());

            Object message = fieldApplier.createAndApply(
                    supportedMessage.getMessageClass(),
                    request.fields()
            );

            byte[] payload = serializer.serialize(
                    message,
                    parseByteOrder(reflectionInterface.getByteOrder())
            );

            List<PublishTarget> targets = resolveTargets(request, reflectionInterface);

            for (PublishTarget target : targets) {
                trafficPublisher.publish(target, payload);
            }

            String primaryHost = targets.get(0).host();
            int primaryPort = targets.get(0).port();
            List<String> targetLabels = targets.stream()
                    .map(target -> target.host() + ":" + target.port())
                    .toList();

            return new PublisherSendResponse(
                    true,
                    request.interfaceName(),
                    request.opcode(),
                    supportedMessage.getMessageClass(),
                    primaryHost,
                    primaryPort,
                    targetLabels,
                    payload.length,
                    null
            );
        } catch (Exception e) {
            return new PublisherSendResponse(
                    false,
                    request == null ? null : request.interfaceName(),
                    request == null ? null : request.opcode(),
                    null,
                    request == null ? null : request.host(),
                    request == null ? 0 : request.port(),
                    List.of(),
                    0,
                    e.getMessage()
            );
        }
    }

    private String requireInterfaceName(PublisherSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }

        if (request.interfaceName() == null || request.interfaceName().isBlank()) {
            throw new IllegalArgumentException("interfaceName is required");
        }

        return request.interfaceName();
    }

    private void validate(PublisherSendRequest request, TrafficMonitorProperties.ReflectionInterface reflectionInterface) {
        requireInterfaceName(request);

        if (request.opcode() == null || request.opcode().isBlank()) {
            throw new IllegalArgumentException("opcode is required");
        }

        if (reflectionInterface.isShouldBroadcast()) {
            if (reflectionInterface.getBroadcastTargets() == null || reflectionInterface.getBroadcastTargets().isEmpty()) {
                throw new IllegalArgumentException(
                        "Broadcast interface " + reflectionInterface.getName() + " must define at least one broadcast target"
                );
            }

            for (String configuredTarget : reflectionInterface.getBroadcastTargets()) {
                parseConfiguredTarget(configuredTarget);
            }

            return;
        }

        if (request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }

        if (request.port() <= 0 || request.port() > 65535) {
            throw new IllegalArgumentException("Invalid port: " + request.port());
        }
    }

    private List<PublishTarget> resolveTargets(
            PublisherSendRequest request,
            TrafficMonitorProperties.ReflectionInterface reflectionInterface
    ) {
        if (!reflectionInterface.isShouldBroadcast()) {
            return List.of(new PublishTarget(TransportProtocol.UDP, request.host(), request.port()));
        }

        Set<String> uniqueTargets = new LinkedHashSet<>(reflectionInterface.getBroadcastTargets());
        List<PublishTarget> result = new ArrayList<>();

        for (String configuredTarget : uniqueTargets) {
            HostPort hostPort = parseConfiguredTarget(configuredTarget);
            result.add(new PublishTarget(TransportProtocol.UDP, hostPort.host(), hostPort.port()));
        }

        return result;
    }

    private HostPort parseConfiguredTarget(String configuredTarget) {
        if (configuredTarget == null || configuredTarget.isBlank()) {
            throw new IllegalArgumentException("Broadcast target must be in the form ip:port");
        }

        String value = configuredTarget.trim();
        int separatorIndex = value.lastIndexOf(':');

        if (separatorIndex <= 0 || separatorIndex == value.length() - 1) {
            throw new IllegalArgumentException(
                    "Invalid broadcast target '" + configuredTarget + "'. Expected format ip:port"
            );
        }

        String host = value.substring(0, separatorIndex).trim();
        String portValue = value.substring(separatorIndex + 1).trim();

        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        if (host.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid broadcast target '" + configuredTarget + "'. Host is required"
            );
        }

        final int port;

        try {
            port = Integer.parseInt(portValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid broadcast target '" + configuredTarget + "'. Port must be numeric",
                    e
            );
        }

        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException(
                    "Invalid broadcast target '" + configuredTarget + "'. Port must be in range 1..65535"
            );
        }

        return new HostPort(host, port);
    }

    private ByteOrder parseByteOrder(String value) {
        if (value == null || value.isBlank() || "BIG_ENDIAN".equalsIgnoreCase(value)) {
            return ByteOrder.BIG_ENDIAN;
        }

        if ("LITTLE_ENDIAN".equalsIgnoreCase(value)) {
            return ByteOrder.LITTLE_ENDIAN;
        }

        throw new IllegalArgumentException("Unsupported byteOrder: " + value);
    }

    private record HostPort(String host, int port) {
    }
}
