package com.example.monitor.rest;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST analogue of {@code MessageSchemaWiringConfig.interfaceMessageDefinitionRegistries} - one
 * {@link RestApiDefinition} per {@code protocol: REST} interface, keyed by {@link
 * InterfaceConfig#getKey()}, built by parsing that interface's {@code swaggerFile} once at
 * startup. Same "must use {@code @Qualifier("restApiDefinitions")}" gotcha as that bean applies
 * here too (see CLAUDE.md's Spring {@code Map<String,X>} bean-injection note): any class injecting
 * this map alongside other {@link RestApiDefinition} beans in the context needs the qualifier, or
 * Spring's implicit "collect all beans of this type by bean name" behavior silently replaces it.
 */
@Configuration
public class RestSchemaWiringConfig {

    @Bean
    public Map<String, RestApiDefinition> restApiDefinitions(
            TrafficToolConfig config, RestSwaggerLoader swaggerLoader, RestApiDefinitionBuilder apiDefinitionBuilder) {
        Map<String, RestApiDefinition> definitions = new LinkedHashMap<>();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            if (!"REST".equalsIgnoreCase(interfaceConfig.getProtocol())) {
                continue;
            }

            OpenAPI openApi = swaggerLoader.loadResolved(Paths.get(interfaceConfig.getSwaggerFile()));
            definitions.put(interfaceConfig.getKey(), apiDefinitionBuilder.build(interfaceConfig.getKey(), openApi));
        }

        return definitions;
    }
}
