package com.example.publisher.config;

import com.example.monitor.rest.RestApiDefinitionBuilder;
import com.example.monitor.rest.RestSchemaConverter;
import com.example.monitor.rest.RestSwaggerLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Explicit {@code @Bean} wiring for traffic-monitor-app-core's REST discovery classes
 * ({@code com.example.monitor.rest}, plain classes whose {@code @Component} annotation is inert
 * here since that package isn't component-scanned) - needed as constructor dependencies for
 * {@link com.example.monitor.rest.RestSchemaWiringConfig#restApiDefinitions} (see {@link
 * ReusedCoreConfig}). These three stay in traffic-monitor-app-core because REST *ingestion* still
 * needs them too; {@code RestFieldMetadataService}/{@code RestRequestBodyAssembler} do not (they're
 * publish-only) and have their own copies under {@code com.example.publisher.rest} instead, kept
 * independent of the {@code com.example.monitor.publisher} package being deleted from -core.
 */
@Configuration
public class RestSupportBeans {

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
}
