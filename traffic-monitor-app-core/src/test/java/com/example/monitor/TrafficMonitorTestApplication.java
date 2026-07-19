package com.example.monitor;

import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Test-only {@code @SpringBootApplication}, used only to anchor this module's Spring slice tests
 * (@WebMvcTest, @JdbcTest, etc. auto-discover the nearest @SpringBootConfiguration by package).
 * traffic-monitor-app-core has no compile dependency on concrete message/handler classes, so
 * unlike traffic-monitor-app's real application class, this doesn't scan com.example.messagehandlers
 * &mdash; the full-wiring integration test suite lives in traffic-monitor-app instead, where those
 * classes are actually on the classpath.
 */
@SpringBootApplication(scanBasePackages = {"com.example.monitor"})
@EnableConfigurationProperties(TrafficMonitorProperties.class)
public class TrafficMonitorTestApplication {
}
