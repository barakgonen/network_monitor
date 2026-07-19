package com.example.monitor.publisher;

import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.example.monitor.schema.InterfaceConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ProtocolHeaderCodec;
import com.example.schemacore.ReflectiveMessageDefinition;
import com.example.schemas.candy.CandyMessage;
import com.example.schemas.rada.messages.RadaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PublisherServiceTest {

    @Mock
    private UdpMessagePublisher udpMessagePublisher;

    @Mock
    private TcpMessagePublisher tcpMessagePublisher;

    private InterfaceConfig candyConfig;
    private InterfaceConfig radaConfig;
    private PublisherService service;

    @BeforeEach
    void setUp() {
        candyConfig = new InterfaceConfig();
        candyConfig.setKey("candy");
        candyConfig.setName("Candy Interface");

        radaConfig = new InterfaceConfig();
        radaConfig.setKey("rada");
        radaConfig.setName("Rada Interface");
        radaConfig.setPort(5050);

        MessageDefinitionRegistry candyRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Candy Interface", "Candy", 4001, CandyMessage.class)));
        MessageDefinitionRegistry radaRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Rada Interface", "RadaStatus", 3, RadaStatus.class)));

        PublisherMetadataService metadataService = new PublisherMetadataService(
                buildConfig(), candyRegistry, Map.of("rada", radaRegistry));

        service = new PublisherService(metadataService, udpMessagePublisher, tcpMessagePublisher);
    }

    private com.example.monitor.schema.TrafficToolConfig buildConfig() {
        com.example.monitor.schema.TrafficToolConfig config = new com.example.monitor.schema.TrafficToolConfig();
        config.setInterfaces(List.of(candyConfig, radaConfig));
        return config;
    }

    @Test
    void send_forLegacyInterface_wrapsBodyWithEnvelopeHeader() {
        PublisherSendRequest request = new PublisherSendRequest(
                "candy", "Candy", "localhost", 7001, "UDP",
                Map.of("name", "lollipop", "calories", 80.0));

        PublisherSendResponse response = service.send(request);

        assertThat(response.success()).isTrue();
        assertThat(response.targets()).containsExactly("localhost:7001");

        var captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(udpMessagePublisher).send(eq("localhost"), eq(7001), captor.capture());

        byte[] sent = captor.getValue();
        var header = ProtocolHeaderCodec.decodeHeader(ByteBuffer.wrap(sent));
        assertThat(header.opcode()).isEqualTo(4001);
    }

    @Test
    void send_forDedicatedPortInterface_sendsBodyWithoutExtraEnvelopeWrap() {
        PublisherSendRequest request = new PublisherSendRequest(
                "rada", "RadaStatus", "localhost", 5050, "UDP", Map.of());

        PublisherSendResponse response = service.send(request);

        assertThat(response.success()).isTrue();

        var captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(udpMessagePublisher).send(eq("localhost"), eq(5050), captor.capture());

        // RadaStatus embeds its own 16-byte header directly (no ProtocolHeaderCodec envelope).
        RadaStatus decoded = com.example.schemacore.ReflectiveStructCodec.decode(RadaStatus.class, captor.getValue());
        assertThat(decoded).isNotNull();
    }

    @Test
    void send_withTcpTransport_usesTcpPublisher() {
        PublisherSendRequest request = new PublisherSendRequest(
                "candy", "Candy", "localhost", 7001, "TCP", Map.of("name", "x", "calories", 1.0));

        service.send(request);

        verify(tcpMessagePublisher).send(eq("localhost"), eq(7001), any());
        verify(udpMessagePublisher, never()).send(anyString(), anyInt(), any());
    }

    @Test
    void send_broadcasts_whenInterfaceConfiguredToBroadcast() {
        radaConfig.setShouldBroadcast(true);
        radaConfig.setBroadcastTargets(List.of("10.0.0.1:5050", "10.0.0.2:5050"));

        PublisherSendRequest request = new PublisherSendRequest("rada", "RadaStatus", null, null, null, Map.of());

        PublisherSendResponse response = service.send(request);

        assertThat(response.success()).isTrue();
        assertThat(response.targets()).containsExactlyInAnyOrder("10.0.0.1:5050", "10.0.0.2:5050");
        verify(udpMessagePublisher).send(eq("10.0.0.1"), eq(5050), any());
        verify(udpMessagePublisher).send(eq("10.0.0.2"), eq(5050), any());
    }

    @Test
    void send_withUnknownMessageType_returnsFailureResponse() {
        PublisherSendResponse response = service.send(
                new PublisherSendRequest("candy", "Unknown", "localhost", 7001, "UDP", Map.of()));

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("Unknown message type");
    }

    @Test
    void send_withNoDestinationAndNoBroadcast_returnsFailureResponse() {
        PublisherSendResponse response = service.send(
                new PublisherSendRequest("candy", "Candy", null, null, null, Map.of("name", "x", "calories", 1.0)));

        assertThat(response.success()).isFalse();
        assertThat(response.error()).contains("No destination");
    }
}
