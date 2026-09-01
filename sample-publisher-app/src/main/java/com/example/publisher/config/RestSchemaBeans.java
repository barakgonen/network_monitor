package com.example.publisher.config;

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
 * Explicit {@code @Bean} wiring for the {@code rest-schema} module's plain classes (no
 * {@code @Component} there - that module has no Spring dependency at all) plus the
 * {@code restApiDefinitions} map bean itself, which previously came for free via
 * {@code @Import(RestSchemaWiringConfig.class)} on traffic-monitor-app-core's own bean.
 * {@code RestSchemaWiringConfig} stayed behind in -core (it's ingestion-wiring glue), so this
 * inlines the same discovery loop directly - see that class for the original.
 */
@Configuration
public class RestSchemaBeans {

    @Bean
    public RestSwaggerLoader restSwaggerLoader() {
        return new RestSwaggerLoader();
    }

    @Bean
    public RestSchemaConverter restSchemaConverter() {
        return new RestSchemaConverter();
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
