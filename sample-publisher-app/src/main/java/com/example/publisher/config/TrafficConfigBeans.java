package com.example.publisher.config;

import com.example.trafficconfig.TrafficToolConfig;
import com.example.trafficconfig.TrafficToolConfigLoader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Paths;

/**
 * Loads the same {@code config/traffic-tool.yml} traffic-monitor-app reads, via the standalone
 * {@code traffic-config} module - previously came for free via
 * {@code @Import(MessageSchemaWiringConfig.class)} on traffic-monitor-app-core's own bean, but
 * that class stayed behind in -core (it's ingestion-wiring glue, not reusable data) once this app
 * stopped depending on that module. Mirrors {@code MessageSchemaWiringConfig.trafficToolConfig}
 * exactly, so both apps parse the same config file identically.
 */
@Configuration
public class TrafficConfigBeans {

    @Bean
    public TrafficToolConfig trafficToolConfig(@Value("${traffic.tool.config-path:}") String configPath) {
        TrafficToolConfigLoader loader = new TrafficToolConfigLoader();
        return configPath.isBlank() ? loader.load() : loader.load(Paths.get(configPath));
    }
}
