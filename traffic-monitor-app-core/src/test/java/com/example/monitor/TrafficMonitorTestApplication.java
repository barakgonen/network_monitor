package com.example.monitor;

import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Test-only {@code @SpringBootApplication} used to boot the context for this module's
 * integration tests. traffic-monitor-app-core is a library with no bootable main of its own;
 * the real entry point lives in traffic-monitor-app.
 */
@SpringBootApplication(scanBasePackages = {"com.example.monitor", "com.example.handlerapp"})
@EnableConfigurationProperties(TrafficMonitorProperties.class)
public class TrafficMonitorTestApplication {
}
