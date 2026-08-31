package com.example.monitor.api;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import com.example.schemacore.MessageDefinitionRegistry;
import com.example.schemacore.binaryserdes.MessageType;
import com.example.schemacore.binaryserdes.ProtocolIn;
import com.example.schemacore.binaryserdes.ProtocolOut;
import com.example.schemacore.binaryserdes.SerdesMessageDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceCatalogServiceTest {

    /**
     * The whole reason this class exists: unlike the deleted reflective Generic Publisher, this
     * must list serdes-backed interfaces (no backing {@code Class<?>}, {@code messageClass()} is
     * null) without ever touching {@code messageClass()} at all.
     */
    @Test
    void list_resolvesSerdesBackedInterfaceMessageTypesWithoutTouchingMessageClass() {
        InterfaceConfig candy = new InterfaceConfig();
        candy.setKey("candy");
        candy.setName("Candy Interface");
        candy.setPort(5004);
        candy.setProtocol("TCP");
        candy.setSerdesFile("serdes/candy.protocol.json");

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(candy));

        ProtocolIn protocolIn = ProtocolIn.create().registerMessage(
                MessageType.builder().name("Candy").opcode(4001).build());
        ProtocolOut protocolOut = ProtocolOut.create().registerMessage(
                MessageType.builder().name("Candy").opcode(4001).build());
        MessageDefinitionRegistry serdesRegistry = new MessageDefinitionRegistry(
                List.of(new SerdesMessageDefinition("Candy Interface", "Candy", 4001, protocolIn, protocolOut)));

        InterfaceCatalogService service = new InterfaceCatalogService(config, Map.of("candy", serdesRegistry));

        List<InterfaceCatalogEntryDto> entries = service.list();

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).name()).isEqualTo("Candy Interface");
        assertThat(entries.get(0).messages()).containsExactly(new InterfaceCatalogMessageDto("Candy"));
    }

    @Test
    void list_forRestInterface_listsWithNoMessageTypeSubFilters() {
        InterfaceConfig pets = new InterfaceConfig();
        pets.setKey("pets");
        pets.setName("Pets REST Interface");
        pets.setPort(5060);
        pets.setProtocol("REST");

        TrafficToolConfig config = new TrafficToolConfig();
        config.setInterfaces(List.of(pets));

        InterfaceCatalogService service = new InterfaceCatalogService(config, Map.of());

        List<InterfaceCatalogEntryDto> entries = service.list();

        assertThat(entries).containsExactly(new InterfaceCatalogEntryDto("pets", "Pets REST Interface", List.of()));
    }
}
