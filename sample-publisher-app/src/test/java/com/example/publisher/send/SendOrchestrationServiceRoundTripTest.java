package com.example.publisher.send;

import com.example.schemacore.binaryserdes.Protocol;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemacore.envelope.ProtocolHeader;
import com.example.schemacore.envelope.ProtocolHeaderCodec;
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
}
