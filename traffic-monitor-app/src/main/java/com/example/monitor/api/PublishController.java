package com.example.monitor.api;

import com.example.monitor.publishing.MonitorPayloadFactory;
import com.example.monitor.publishing.UdpMessagePublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublishController {
    private final MonitorPayloadFactory payloadFactory;
    private final UdpMessagePublisher udpMessagePublisher;

    public PublishController(MonitorPayloadFactory payloadFactory, UdpMessagePublisher udpMessagePublisher) {
        this.payloadFactory = payloadFactory;
        this.udpMessagePublisher = udpMessagePublisher;
    }

    @PostMapping("/api/publish/udp")
    public PublishResponse publishUdp(@RequestBody PublishRequest request) {
        try {
            validate(request);

            byte[] payload = payloadFactory.create(
                    request.interfaceName(),
                    request.messageType(),
                    request.fields()
            );

            udpMessagePublisher.send(request.host(), request.port(), payload);

            return new PublishResponse(
                    true,
                    request.interfaceName(),
                    request.messageType(),
                    request.host(),
                    request.port(),
                    payload.length,
                    null
            );
        } catch (Exception e) {
            return new PublishResponse(
                    false,
                    request == null ? null : request.interfaceName(),
                    request == null ? null : request.messageType(),
                    request == null ? null : request.host(),
                    request == null ? 0 : request.port(),
                    0,
                    e.getMessage()
            );
        }
    }

    private void validate(PublishRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request body is required");
        }

        if (request.interfaceName() == null || request.interfaceName().isBlank()) {
            throw new IllegalArgumentException("interfaceName is required");
        }

        if (request.messageType() == null || request.messageType().isBlank()) {
            throw new IllegalArgumentException("messageType is required");
        }

        if (request.host() == null || request.host().isBlank()) {
            throw new IllegalArgumentException("host is required");
        }

        if (request.port() <= 0 || request.port() > 65535) {
            throw new IllegalArgumentException("Invalid UDP port: " + request.port());
        }
    }
}
