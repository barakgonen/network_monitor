package com.example.monitor.publisher;

import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TransportProtocol;

import com.example.monitor.api.publisher.PublisherSendRequest;
import com.example.monitor.api.publisher.PublisherSendResponse;
import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.stereotype.Service;

import java.nio.ByteOrder;

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
            validate(request);

            TrafficMonitorProperties.ReflectionInterface reflectionInterface =
                    metadataService.requireInterface(request.interfaceName());

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

            trafficPublisher.publish(new PublishTarget(TransportProtocol.UDP, request.host(), request.port()), payload);

            return new PublisherSendResponse(
                    true,
                    request.interfaceName(),
                    request.opcode(),
                    supportedMessage.getMessageClass(),
                    request.host(),
                    request.port(),
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
                    0,
                    e.getMessage()
            );
        }
    }

    private void validate(PublisherSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }

        if (request.interfaceName() == null || request.interfaceName().isBlank()) {
            throw new IllegalArgumentException("interfaceName is required");
        }

        if (request.opcode() == null || request.opcode().isBlank()) {
            throw new IllegalArgumentException("opcode is required");
        }

        if (request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }

        if (request.port() <= 0 || request.port() > 65535) {
            throw new IllegalArgumentException("Invalid port: " + request.port());
        }
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
}
