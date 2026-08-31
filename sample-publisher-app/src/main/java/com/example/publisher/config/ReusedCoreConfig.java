package com.example.publisher.config;

import com.example.monitor.rest.RestSchemaWiringConfig;
import com.example.monitor.schema.MessageSchemaWiringConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Pulls in traffic-monitor-app-core's own Spring wiring classes directly via {@code @Import},
 * rather than {@code @ComponentScan}ning {@code com.example.monitor} (which would also drag in
 * ingestion/persistence/auto-reply machinery this app has no business booting). This gives
 * {@code TrafficToolConfig} (reads the same {@code config/traffic-tool.yml}) and
 * {@code Map<String, MessageDefinitionRegistry> interfaceMessageDefinitionRegistries}/
 * {@code Map<String, RestApiDefinition> restApiDefinitions} for free, guaranteeing this app parses
 * serdes/swagger files identically to traffic-monitor-app itself.
 */
@Configuration
@Import({MessageSchemaWiringConfig.class, RestSchemaWiringConfig.class})
public class ReusedCoreConfig {
}
