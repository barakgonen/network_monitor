package com.example.monitor.publisher;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.MessageConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
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

    @Test
    void requireInterfaceConfig_withUnknownKey_throws() {
        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of());

        PublisherMetadataService service = new PublisherMetadataService(config, Map.of());

        assertThatThrownBy(() -> service.requireInterfaceConfig("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
