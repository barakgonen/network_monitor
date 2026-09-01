package com.example.monitor.api;

import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import com.example.schemacore.MessageDefinition;
import com.example.schemacore.MessageDefinitionRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lists interfaces/message-types for the viewer's sidebar filter chips and History tab's
 * interface dropdown - the replacement for what {@code PublisherMetadataService}/{@code
 * PublisherInterfaceDto} used to serve via {@code /api/publisher/interfaces} before publishing
 * moved to sample-publisher-app (see CLAUDE.md's "Publishing lives in sample-publisher-app, not
 * here"). Deliberately does not use {@code MessageDefinition#messageClass()} at all (unlike the
 * deleted reflective Generic Publisher) - message-type names come straight off {@link
 * MessageDefinition#messageType()}, which works identically for serdes-backed and hand-written
 * messages alike.
 */
@Component
public class InterfaceCatalogService {

    private final TrafficToolConfig trafficToolConfig;
    private final Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries;

    public InterfaceCatalogService(
            TrafficToolConfig trafficToolConfig,
            @Qualifier("interfaceMessageDefinitionRegistries") Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries
    ) {
        this.trafficToolConfig = trafficToolConfig;
        this.interfaceMessageDefinitionRegistries = interfaceMessageDefinitionRegistries;
    }

    public List<InterfaceCatalogEntryDto> list() {
        List<InterfaceCatalogEntryDto> result = new ArrayList<>();

        for (InterfaceConfig interfaceConfig : trafficToolConfig.getInterfaces()) {
            result.add(entryFor(interfaceConfig));
        }

        return result;
    }

    private InterfaceCatalogEntryDto entryFor(InterfaceConfig interfaceConfig) {
        // REST interfaces have no MessageDefinitionRegistry entry at all (see
        // MessageSchemaWiringConfig) - listed with no message-type sub-filters, same as before.
        if ("REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
            return new InterfaceCatalogEntryDto(interfaceConfig.getKey(), interfaceConfig.getName(), List.of());
        }

        MessageDefinitionRegistry registry = interfaceMessageDefinitionRegistries.get(interfaceConfig.getKey());
        List<MessageDefinition> definitions = registry != null ? registry.all() : List.of();
        String displayName = definitions.isEmpty() ? interfaceConfig.getName() : definitions.get(0).interfaceName();

        List<InterfaceCatalogMessageDto> messages = definitions.stream()
                .map(definition -> new InterfaceCatalogMessageDto(definition.messageType()))
                .toList();

        return new InterfaceCatalogEntryDto(interfaceConfig.getKey(), displayName, messages);
    }
}
