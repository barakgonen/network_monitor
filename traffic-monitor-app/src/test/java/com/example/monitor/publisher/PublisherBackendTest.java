//package com.example.monitor.publisher;
//
//import com.example.monitor.api.publisher.PublisherInterfaceDto;
//import com.example.monitor.api.publisher.PublisherSendRequest;
//import com.example.monitor.api.publisher.PublisherSendResponse;
//import com.example.monitor.config.TrafficMonitorProperties;
//import com.example.monitor.publisher.testmodel.DirectByteArrayMessage;
//import com.example.monitor.publisher.testmodel.FixedByteBufferMessage;
//import com.example.monitor.publisher.testmodel.TestStatus;
//import org.junit.jupiter.api.Test;
//
//import java.net.DatagramPacket;
//import java.net.DatagramSocket;
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class PublisherBackendTest {
//
//    @Test
//    void metadataDiscoversNestedEnumAndComplexArrayFields() {
//        PublisherFieldMetadataService service = new PublisherFieldMetadataService();
//
//        var fields = service.fieldsForClass(DirectByteArrayMessage.class.getName());
//
//        var header = requireField(fields, "header");
//        assertEquals("object", header.kind());
//        assertTrue(header.children().stream().anyMatch(field -> field.name().equals("opcode")));
//        assertTrue(header.children().stream().anyMatch(field -> field.name().equals("sequence")));
//
//        var status = requireField(fields, "status");
//        assertEquals("enum", status.kind());
//        assertEquals(List.of("IDLE", "ACTIVE", "ERROR"), status.enumValues());
//
//        var plots = requireField(fields, "plots");
//        assertEquals("array", plots.kind());
//        assertEquals(3, plots.arrayLength());
//        assertTrue(plots.children().stream().anyMatch(field -> field.name().equals("x")));
//        assertTrue(plots.children().stream().anyMatch(field -> field.name().equals("y")));
//    }
//
//    @Test
//    void fieldApplierAppliesNestedObjectsEnumsAndPartialComplexArrays() {
//        ReflectionFieldApplier applier = new ReflectionFieldApplier();
//
//        var fields = new LinkedHashMap<String, Object>();
//        fields.put("header", Map.of("opcode", 9001, "sequence", 7));
//        fields.put("latitude", 32.123);
//        fields.put("status", "ACTIVE");
//        fields.put("plots", List.of(
//                Map.of("x", 1.5, "y", 2.5),
//                Map.of("x", 3.5, "y", 4.5)
//        ));
//
//        DirectByteArrayMessage message = (DirectByteArrayMessage) applier.createAndApply(
//                DirectByteArrayMessage.class.getName(),
//                fields
//        );
//
//        assertEquals(9001, message.header.opcode);
//        assertEquals(7, message.header.sequence);
//        assertEquals(32.123, message.latitude);
//        assertEquals(TestStatus.ACTIVE, message.status);
//        assertNotNull(message.plots[0]);
//        assertNotNull(message.plots[1]);
//        assertNotNull(message.plots[2], "unprovided fixed array rows should be initialized to defaults");
//        assertEquals(1.5f, message.plots[0].x);
//        assertEquals(2.5f, message.plots[0].y);
//        assertEquals(3.5f, message.plots[1].x);
//        assertEquals(4.5f, message.plots[1].y);
//        assertEquals(0.0f, message.plots[2].x);
//        assertEquals(0.0f, message.plots[2].y);
//    }
//
//    @Test
//    void fieldApplierRejectsArrayInputAboveFixedLength() {
//        ReflectionFieldApplier applier = new ReflectionFieldApplier();
//
//        var fields = Map.<String, Object>of(
//                "plots", List.of(
//                        Map.of("x", 1, "y", 1),
//                        Map.of("x", 2, "y", 2),
//                        Map.of("x", 3, "y", 3),
//                        Map.of("x", 4, "y", 4)
//                )
//        );
//
//        IllegalArgumentException ex = assertThrows(
//                IllegalArgumentException.class,
//                () -> applier.createAndApply(DirectByteArrayMessage.class.getName(), fields)
//        );
//
//        assertTrue(ex.getMessage().contains("max size is 3"));
//    }
//
//    @Test
//    void serializerUsesByteBufferContractForFixedStructs() {
//        ReflectionMessageSerializer serializer = new ReflectionMessageSerializer();
//        FixedByteBufferMessage message = new FixedByteBufferMessage();
//        message.first = 11;
//        message.second = 22;
//        message.third = 33.5;
//
//        byte[] bytes = serializer.serialize(message, ByteOrder.BIG_ENDIAN);
//        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
//
//        assertEquals(Integer.BYTES + Short.BYTES + Double.BYTES, bytes.length);
//        assertEquals(11, buffer.getInt());
//        assertEquals(22, buffer.getShort());
//        assertEquals(33.5, buffer.getDouble());
//        assertFalse(buffer.hasRemaining());
//    }
//
//    @Test
//    void serializerPrefersDirectByteArrayContractForComplexArrayMessages() {
//        ReflectionFieldApplier applier = new ReflectionFieldApplier();
//        ReflectionMessageSerializer serializer = new ReflectionMessageSerializer();
//
//        DirectByteArrayMessage message = (DirectByteArrayMessage) applier.createAndApply(
//                DirectByteArrayMessage.class.getName(),
//                Map.of(
//                        "header", Map.of("opcode", 55, "sequence", 9),
//                        "latitude", 12.25,
//                        "status", "ERROR",
//                        "plots", List.of(Map.of("x", 7.5, "y", 8.5))
//                )
//        );
//
//        byte[] bytes = serializer.serialize(message, ByteOrder.BIG_ENDIAN);
//        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
//
//        assertEquals(39, bytes.length);
//        assertEquals(55, buffer.getInt());
//        assertEquals(9, buffer.getShort());
//        assertEquals(12.25, buffer.getDouble());
//        assertEquals((byte) TestStatus.ERROR.ordinal(), buffer.get());
//        assertEquals(7.5f, buffer.getFloat());
//        assertEquals(8.5f, buffer.getFloat());
//    }
//
//    @Test
//    void publisherServiceSendsSerializedMessageOverUdp() throws Exception {
//        TrafficMonitorProperties properties = new TrafficMonitorProperties();
//        TrafficMonitorProperties.ReflectionInterface reflectionInterface = new TrafficMonitorProperties.ReflectionInterface();
//        reflectionInterface.setName("Test Interface");
//        reflectionInterface.setProtocol("UDP");
//        reflectionInterface.setPort(12345);
//        reflectionInterface.setByteOrder("BIG_ENDIAN");
//        reflectionInterface.setHeaderType("unused.in.this.test");
//        reflectionInterface.setOpcodeFieldName("opcode");
//
//        TrafficMonitorProperties.SupportedMessage supportedMessage = new TrafficMonitorProperties.SupportedMessage();
//        supportedMessage.setDisplayName("DirectByteArrayMessage");
//        supportedMessage.setMessageClass(DirectByteArrayMessage.class.getName());
//        reflectionInterface.setSupportedMessages(Map.of("55", supportedMessage));
//        properties.setReflectionInterfaces(List.of(reflectionInterface));
//
//        PublisherFieldMetadataService fieldMetadataService = new PublisherFieldMetadataService();
//        PublisherMetadataService metadataService = new PublisherMetadataService(properties, fieldMetadataService);
//        PublisherService publisherService = new PublisherService(
//                metadataService,
//                new ReflectionFieldApplier(),
//                new ReflectionMessageSerializer(),
//                new UdpPublisher()
//        );
//
//        try (DatagramSocket socket = new DatagramSocket(0)) {
//            socket.setSoTimeout(2_000);
//            int port = socket.getLocalPort();
//
//            PublisherSendRequest request = new PublisherSendRequest(
//                    "Test Interface",
//                    "55",
//                    "127.0.0.1",
//                    port,
//                    Map.of(
//                            "header", Map.of("opcode", 55, "sequence", 9),
//                            "latitude", 12.25,
//                            "status", "ERROR",
//                            "plots", List.of(Map.of("x", 7.5, "y", 8.5))
//                    )
//            );
//
//            PublisherSendResponse response = publisherService.send(request);
//            assertTrue(response.success(), () -> "Publisher failed: " + response.error());
//            assertEquals(39, response.bytesSent());
//
//            byte[] receiveBuffer = new byte[128];
//            DatagramPacket packet = new DatagramPacket(receiveBuffer, receiveBuffer.length);
//            socket.receive(packet);
//
//            assertEquals(39, packet.getLength());
//            ByteBuffer buffer = ByteBuffer.wrap(packet.getData(), 0, packet.getLength()).order(ByteOrder.BIG_ENDIAN);
//            assertEquals(55, buffer.getInt());
//            assertEquals(9, buffer.getShort());
//            assertEquals(12.25, buffer.getDouble());
//            assertEquals((byte) TestStatus.ERROR.ordinal(), buffer.get());
//            assertEquals(7.5f, buffer.getFloat());
//            assertEquals(8.5f, buffer.getFloat());
//        }
//    }
//
//    @Test
//    void metadataServiceReturnsConfiguredInterfacesAndMessagesWithFields() {
//        TrafficMonitorProperties properties = new TrafficMonitorProperties();
//        TrafficMonitorProperties.ReflectionInterface reflectionInterface = new TrafficMonitorProperties.ReflectionInterface();
//        reflectionInterface.setName("Test Interface");
//        reflectionInterface.setProtocol("UDP");
//        reflectionInterface.setPort(5050);
//        reflectionInterface.setByteOrder("BIG_ENDIAN");
//        reflectionInterface.setHeaderType("com.example.Header");
//        reflectionInterface.setOpcodeFieldName("opcode");
//
//        TrafficMonitorProperties.SupportedMessage supportedMessage = new TrafficMonitorProperties.SupportedMessage();
//        supportedMessage.setDisplayName("Test Message");
//        supportedMessage.setMessageClass(DirectByteArrayMessage.class.getName());
//        reflectionInterface.setSupportedMessages(Map.of("55", supportedMessage));
//        properties.setReflectionInterfaces(List.of(reflectionInterface));
//
//        PublisherMetadataService service = new PublisherMetadataService(properties, new PublisherFieldMetadataService());
//
//        List<PublisherInterfaceDto> interfaces = service.interfaces();
//
//        assertEquals(1, interfaces.size());
//        assertEquals("Test Interface", interfaces.get(0).name());
//        assertEquals(1, interfaces.get(0).messages().size());
//        assertEquals("55", interfaces.get(0).messages().get(0).opcode());
//        assertFalse(interfaces.get(0).messages().get(0).fields().isEmpty());
//    }
//
//    private com.example.monitor.api.publisher.PublisherFieldDto requireField(
//            List<com.example.monitor.api.publisher.PublisherFieldDto> fields,
//            String name
//    ) {
//        return fields.stream()
//                .filter(field -> field.name().equals(name))
//                .findFirst()
//                .orElseThrow(() -> new AssertionError("Missing field: " + name));
//    }
//}
