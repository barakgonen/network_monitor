package com.example.monitor.rest;

import com.example.restschema.RestApiDefinition;
import com.example.restschema.RestApiDefinitionBuilder;
import com.example.restschema.RestSchemaConverter;
import com.example.restschema.RestSwaggerLoader;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
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
 *
 * <p>Also explicitly wires {@link RestSchemaConverter}/{@link RestSwaggerLoader}/{@link
 * RestApiDefinitionBuilder} as beans - they moved to the standalone {@code rest-schema} module
 * (no Spring dependency there), so they lost their own {@code @Component} annotations and can no
 * longer be picked up by component scanning.
 */
@Configuration
public class RestSchemaWiringConfig {

    @Bean
    public RestSchemaConverter restSchemaConverter() {
        return new RestSchemaConverter();
    }

    @Bean
    public RestSwaggerLoader restSwaggerLoader() {
        return new RestSwaggerLoader();
    }

    @Bean
    public RestApiDefinitionBuilder restApiDefinitionBuilder(RestSchemaConverter restSchemaConverter) {
        return new RestApiDefinitionBuilder(restSchemaConverter);
    }

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
