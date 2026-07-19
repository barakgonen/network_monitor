package com.example.monitor.publisher;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.MessageConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.ReflectiveMessageDefinition;
import com.example.schemas.candy.CandyMessage;
import com.example.schemas.rada.messages.RadaStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublisherMetadataServiceTest {

    @Test
    void interfaces_resolvesOpcodeAndMessageClassFromGlobalRegistry_forLegacyInterface() {
        InterfaceConfig candy = new InterfaceConfig();
        candy.setKey("candy");
        candy.setName("Candy Interface");
        MessageConfig candyMessage = new MessageConfig();
        candyMessage.setType("Candy");
        candy.setMessages(List.of(candyMessage));

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(candy));

        MessageDefinitionRegistry globalRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Candy Interface", "Candy", 4001, CandyMessage.class)));

        PublisherMetadataService service = new PublisherMetadataService(config, globalRegistry, Map.of());

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertThat(interfaces).hasSize(1);
        assertThat(interfaces.get(0).messages()).containsExactly(
                new PublisherMessageDto("Candy", CandyMessage.class.getName(), 4001));
    }

    @Test
    void interfaces_resolvesFromScopedRegistry_forDedicatedPortInterface() {
        InterfaceConfig rada = new InterfaceConfig();
        rada.setKey("rada");
        rada.setName("Rada Interface");
        rada.setPort(5050);
        MessageConfig radaMessage = new MessageConfig();
        radaMessage.setType("RadaStatus");
        rada.setMessages(List.of(radaMessage));

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(rada));

        MessageDefinitionRegistry scopedRegistry = new MessageDefinitionRegistry(
                List.of(new ReflectiveMessageDefinition("Rada Interface", "RadaStatus", 3, RadaStatus.class)));

        PublisherMetadataService service =
                new PublisherMetadataService(config, new MessageDefinitionRegistry(List.of()), Map.of("rada", scopedRegistry));

        List<PublisherInterfaceDto> interfaces = service.interfaces();

        assertThat(interfaces.get(0).messages()).containsExactly(
                new PublisherMessageDto("RadaStatus", RadaStatus.class.getName(), 3));
    }

    @Test
    void requireInterfaceConfig_withUnknownKey_throws() {
        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of());

        PublisherMetadataService service =
                new PublisherMetadataService(config, new MessageDefinitionRegistry(List.of()), Map.of());

        assertThatThrownBy(() -> service.requireInterfaceConfig("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
