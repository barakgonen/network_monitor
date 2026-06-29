package com.example.specific.monitor;

import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {
        "com.example.monitor.*",
        "com.example.specific.monitor"
})
@EnableConfigurationProperties(TrafficMonitorProperties.class)
public class SpecificTrafficMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpecificTrafficMonitorApplication.class, args);
    }
}

