package com.example.monitor.schema;

import com.example.schemacore.MessageDefinitionRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class MessageSchemaWiringConfig {

    @Bean
    public TrafficToolConfig trafficToolConfig() {
        return new TrafficToolConfigLoader().load();
    }

    @Bean
    public MessageDefinitionRegistry messageDefinitionRegistry(TrafficToolConfig config) throws ReflectiveOperationException {
        List<String> definitionClassNames = new ArrayList<>();
        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            for (MessageConfig message : interfaceConfig.getMessages()) {
                definitionClassNames.add(message.getDefinitionClass());
            }
        }

        return MessageDefinitionRegistry.loadFromClassNames(definitionClassNames);
    }
}
