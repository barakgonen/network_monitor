package com.example.monitor.publisher;

import com.example.messagereader.api.PublishTarget;
import com.example.messagereader.api.TrafficPublisher;
import com.example.monitor.api.publisher.PublisherInterfaceDto;
import com.example.monitor.api.publisher.PublisherSendRequest;
import com.example.monitor.api.publisher.PublisherSendResponse;
import com.example.monitor.config.TrafficMonitorProperties;
import com.example.monitor.publisher.testmodel.DirectByteArrayMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PublisherBroadcastSupportTest {

    @Test
    void metadataExposesBroadcastSettings() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();
        properties.setReflectionInterfaces(List.of(configuredInterface(List.of(
                "192.168.10.255:5050",
                "10.0.0.25:6060"
        ))));

        PublisherMetadataService service = new PublisherMetadataService(properties, new PublisherFieldMetadataService());

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertEquals(1, interfaces.size());
        assertTrue(interfaces.getFirst().enabled());
        assertTrue(interfaces.getFirst().broadcast());
        assertEquals(List.of("192.168.10.255:5050", "10.0.0.25:6060"), interfaces.getFirst().broadcastTargets());
    }

    @Test
    void publisherServiceBroadcastsToConfiguredTargets() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();
        properties.setReflectionInterfaces(List.of(configuredInterface(List.of(
                "127.0.0.1:5001",
                "127.0.0.2:5002",
                "127.0.0.1:5001"
        ))));

        RecordingTrafficPublisher trafficPublisher = new RecordingTrafficPublisher();
        PublisherService service = new PublisherService(
                new PublisherMetadataService(properties, new PublisherFieldMetadataService()),
                new ReflectionFieldApplier(),
                new ReflectionMessageSerializer(),
                trafficPublisher
        );

        PublisherSendResponse response = service.send(new PublisherSendRequest(
                "Broadcast Interface",
                "55",
                "ignored-host",
                9999,
                messageFields()
        ));

        assertTrue(response.success(), () -> "Publisher failed: " + response.error());
        assertEquals(List.of("127.0.0.1:5001", "127.0.0.2:5002"), response.targets());
        assertEquals(2, trafficPublisher.targets.size());
        assertEquals("127.0.0.1", trafficPublisher.targets.get(0).host());
        assertEquals(5001, trafficPublisher.targets.get(0).port());
        assertEquals("127.0.0.2", trafficPublisher.targets.get(1).host());
        assertEquals(5002, trafficPublisher.targets.get(1).port());
        assertTrue(response.bytesSent() > 0);
    }

    @Test
    void publisherServiceRejectsBroadcastInterfaceWithoutTargets() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();
        properties.setReflectionInterfaces(List.of(configuredInterface(List.of())));

        PublisherService service = new PublisherService(
                new PublisherMetadataService(properties, new PublisherFieldMetadataService()),
                new ReflectionFieldApplier(),
                new ReflectionMessageSerializer(),
                (target, payload) -> fail("publish should not be called")
        );

        PublisherSendResponse response = service.send(new PublisherSendRequest(
                "Broadcast Interface",
                "55",
                "ignored-host",
                9999,
                messageFields()
        ));

        assertFalse(response.success());
        assertNotNull(response.error());
        assertTrue(response.error().contains("must define at least one broadcast target"));
    }

    private TrafficMonitorProperties.ReflectionInterface configuredInterface(List<String> targets) {
        TrafficMonitorProperties.ReflectionInterface reflectionInterface = new TrafficMonitorProperties.ReflectionInterface();
        reflectionInterface.setName("Broadcast Interface");
        reflectionInterface.setEnabled(true);
        reflectionInterface.setProtocol("UDP");
        reflectionInterface.setPort(5050);
        reflectionInterface.setShouldBroadcast(true);
        reflectionInterface.setBroadcastTargets(new ArrayList<>(targets));
        reflectionInterface.setByteOrder("BIG_ENDIAN");
        reflectionInterface.setHeaderType("unused.in.this.test");
        reflectionInterface.setOpcodeFieldName("opcode");

        TrafficMonitorProperties.SupportedMessage supportedMessage = new TrafficMonitorProperties.SupportedMessage();
        supportedMessage.setDisplayName("DirectByteArrayMessage");
        supportedMessage.setMessageClass(DirectByteArrayMessage.class.getName());

        Map<String, TrafficMonitorProperties.SupportedMessage> supportedMessages = new LinkedHashMap<>();
        supportedMessages.put("55", supportedMessage);
        reflectionInterface.setSupportedMessages(supportedMessages);
        return reflectionInterface;
    }

    private Map<String, Object> messageFields() {
        return Map.of(
                "header", Map.of("opcode", 55, "sequence", 9),
                "latitude", 12.25,
                "status", "ERROR",
                "plots", List.of(Map.of("x", 7.5, "y", 8.5))
        );
    }

    private static class RecordingTrafficPublisher implements TrafficPublisher {
        private final List<PublishTarget> targets = new ArrayList<>();

        @Override
        public void publish(PublishTarget target, byte[] payload) {
            targets.add(target);
            assertNotNull(payload);
            assertTrue(payload.length > 0);
        }
    }
}

