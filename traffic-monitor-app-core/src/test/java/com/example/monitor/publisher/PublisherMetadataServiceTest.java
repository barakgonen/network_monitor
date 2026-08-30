package com.example.monitor.publisher;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.MessageConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.binaryserdes.MessageType;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.SerdesMessageDefinition;
import com.example.schemacore.reflect.ReflectiveMessageDefinition;
import com.example.monitor.publisher.StubMessages.StubDedicatedPortMessage;
import com.example.monitor.publisher.StubMessages.StubLegacyMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublisherMetadataServiceTest {

    @Test
    void interfaces_resolvesOpcodeAndMessageClassFromScopedRegistry_forCandy() {
        InterfaceConfig candy = new InterfaceConfig();
        candy.setKey("candy");
        candy.setName("Candy Interface");
        candy.setPort(5004);
        candy.setProtocol("TCP");
        MessageConfig candyMessage = new MessageConfig();
        candyMessage.setType("Candy");
        candy.setMessages(List.of(candyMessage));

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(candy));

        MessageDefinitionRegistry candyRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Candy Interface", "Candy", 4001, StubLegacyMessage.class)));

        PublisherMetadataService service = new PublisherMetadataService(config, Map.of("candy", candyRegistry));

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertThat(interfaces).hasSize(1);
        assertThat(interfaces.get(0).messages()).containsExactly(
                new PublisherMessageDto("Candy", StubLegacyMessage.class.getName(), 4001));
    }

    @Test
    void interfaces_resolvesFromScopedRegistry_forRada() {
        InterfaceConfig rada = new InterfaceConfig();
        rada.setKey("rada");
        rada.setName("Rada Interface");
        rada.setPort(5050);
        rada.setMessageOwnsHeader(true);
        MessageConfig radaMessage = new MessageConfig();
        radaMessage.setType("RadaStatus");
        rada.setMessages(List.of(radaMessage));

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(rada));

        MessageDefinitionRegistry scopedRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Rada Interface", "RadaStatus", 3, StubDedicatedPortMessage.class)));

        PublisherMetadataService service = new PublisherMetadataService(config, Map.of("rada", scopedRegistry));

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertThat(interfaces.get(0).messages()).containsExactly(
                new PublisherMessageDto("RadaStatus", StubDedicatedPortMessage.class.getName(), 3));
    }

    /**
     * Regression test for the left-sidebar NPE: serdes-backed interfaces (see {@code
     * InterfaceConfig#hasSerdesFile()}) have no {@code messages:} list at all
     * ({@link InterfaceConfig#getMessages()} is {@code null}, messages are auto-discovered from
     * {@code serdesFile} instead) and their {@link SerdesMessageDefinition}s have no backing
     * {@code Class<?>} ({@link com.example.schemacore.MessageDefinition#messageClass()} is
     * {@code null}). {@code /api/publisher/interfaces} must still list them instead of throwing.
     */
    @Test
    void interfaces_resolvesFromScopedRegistry_forSerdesBackedInterfaceWithNoMessagesList() {
        InterfaceConfig candy = new InterfaceConfig();
        candy.setKey("candy");
        candy.setName("Candy Interface");
        candy.setPort(5004);
        candy.setProtocol("TCP");
        candy.setSerdesFile("serdes/candy.protocol.json");
        // Deliberately not calling setMessages(...) - null, exactly like a real serdes-backed
        // interface loaded from config/traffic-tool.yml.

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(candy));

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(
                MessageType.builder().name("Candy").opcode(4001).build());
        ProtocolOut protocolOut = ProtocolOut.create().registerMessage(
                MessageType.builder().name("Candy").opcode(4001).build());
        MessageDefinitionRegistry serdesRegistry = new MessageDefinitionRegistry(
                List.of(new SerdesMessageDefinition("Candy Interface", "Candy", 4001, protocolIn, protocolOut)));

        PublisherMetadataService service = new PublisherMetadataService(config, Map.of("candy", serdesRegistry));

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertThat(interfaces).hasSize(1);
        assertThat(interfaces.get(0).name()).isEqualTo("Candy Interface");
        assertThat(interfaces.get(0).messages()).containsExactly(
                new PublisherMessageDto("Candy", null, 4001));
    }

    @Test
    void requireInterfaceConfig_withUnknownKey_throws() {
        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of());

        PublisherMetadataService service = new PublisherMetadataService(config, Map.of());

        assertThatThrownBy(() -> service.requireInterfaceConfig("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
