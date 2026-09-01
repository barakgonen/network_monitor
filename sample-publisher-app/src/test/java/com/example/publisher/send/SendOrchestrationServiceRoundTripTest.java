package com.example.publisher.send;

import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.binaryserdes.envelope.ProtocolHeader;
import com.example.binaryserdes.envelope.ProtocolHeaderCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real UDP round-trip: send a message through {@link SendOrchestrationService} exactly as the
 * publisher UI would, receive the real bytes off the wire, and decode them back via the serdes
 * engine's own {@link ProtocolIn} - direct proof that the original Generic Publisher bug
 * (crashing on every serdes-backed message because it required a {@code Class<?>}) is fixed for a
 * concrete interface/message.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "traffic.tool.config-path=src/test/resources/traffic-tool-test.yml"
})
class SendOrchestrationServiceRoundTripTest {

    @Autowired
    private SendOrchestrationService sendOrchestrationService;

    @Test
    void send_forSerdesBackedFruitInterface_producesWireBytesThatDecodeBackCorrectly() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("localhost"))) {
            SendRequest request = new SendRequest(
                    "fruit", "Orange", "localhost", receiver.getLocalPort(), "UDP",
                    Map.of("sourceFarm", "north-farm-17", "freshness", "very_fresh"));

            SendResult result = sendOrchestrationService.send(request);
            assertThat(result.success()).isTrue();
            assertThat(result.error()).isNull();
            assertThat(result.bytesSent()).isGreaterThan(0);

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);

            ByteBuffer wire = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
            ProtocolHeader header = ProtocolHeaderCodec.decodeHeader(wire.duplicate().position(0));
            assertThat(header.opcode()).isEqualTo(1001); // Orange's opcode per serdes/fruit.protocol.json

            byte[] body = new byte[wire.remaining() - ProtocolHeaderCodec.HEADER_SIZE_BYTES];
            wire.position(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
            wire.get(body);

            ProtocolConfig protocolConfig;
            try (var in = java.nio.file.Files.newInputStream(Path.of("../serdes/fruit.protocol.json"))) {
                protocolConfig = Protocol.loadConfig(in);
            }
            ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(protocolConfig);
            String json = protocolIn.parse("Orange", body);

            Map<String, Object> decoded = new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
            });
            assertThat(decoded).containsEntry("sourceFarm", "north-farm-17");
            assertThat(decoded).containsEntry("freshness", "very_fresh");
        }
    }

    /**
     * Boundary case for {@code SendOrchestrationService.encodeSerdesBody}'s "over-allocate a
     * buffer sized off the JSON string's byte length, then trim to the bytes actually written"
     * trick: a zero-length variable-length field. The JSON representation of an empty string
     * still costs 2 bytes ({@code ""}), well under the wire encoding's own length-prefix
     * overhead, so this is the smallest input the buffer sizing has to get right.
     */
    @Test
    void send_withEmptyStringFields_encodesAndDecodesEmptyStrings() throws Exception {
        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("localhost"))) {
            SendRequest request = new SendRequest(
                    "fruit", "Orange", "localhost", receiver.getLocalPort(), "UDP",
                    Map.of("sourceFarm", "", "freshness", ""));

            SendResult result = sendOrchestrationService.send(request);
            assertThat(result.success()).isTrue();
            assertThat(result.bytesSent()).isGreaterThan(0);

            byte[] buffer = new byte[2048];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);

            Map<String, Object> decoded = decodeOrangeBody(packet);
            assertThat(decoded).containsEntry("sourceFarm", "").containsEntry("freshness", "");
        }
    }

    /**
     * The other end of the same boundary: a string field large enough that, if the encoder
     * under-allocated (e.g. sized the buffer off a fixed constant instead of the JSON's own
     * byte length), it would overflow. Proves the sizing formula scales with input size rather
     * than happening to work only for the small fixtures the happy-path test above uses.
     */
    @Test
    void send_withLargeStringField_encodesWithoutBufferOverflow() throws Exception {
        String largeValue = "x".repeat(5000);

        try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getByName("localhost"))) {
            SendRequest request = new SendRequest(
                    "fruit", "Orange", "localhost", receiver.getLocalPort(), "UDP",
                    Map.of("sourceFarm", largeValue, "freshness", "very_fresh"));

            SendResult result = sendOrchestrationService.send(request);
            assertThat(result.success()).isTrue();
            assertThat(result.bytesSent()).isGreaterThan(5000);

            byte[] buffer = new byte[16 * 1024];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            receiver.receive(packet);

            Map<String, Object> decoded = decodeOrangeBody(packet);
            assertThat(decoded).containsEntry("sourceFarm", largeValue);
        }
    }

    private Map<String, Object> decodeOrangeBody(DatagramPacket packet) throws Exception {
        ByteBuffer wire = ByteBuffer.wrap(packet.getData(), 0, packet.getLength());
        byte[] body = new byte[wire.remaining() - ProtocolHeaderCodec.HEADER_SIZE_BYTES];
        wire.position(ProtocolHeaderCodec.HEADER_SIZE_BYTES);
        wire.get(body);

        ProtocolConfig protocolConfig;
        try (var in = java.nio.file.Files.newInputStream(Path.of("../serdes/fruit.protocol.json"))) {
            protocolConfig = Protocol.loadConfig(in);
        }
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(protocolConfig);
        String json = protocolIn.parse("Orange", body);

        return new ObjectMapper().readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
        });
    }
}
