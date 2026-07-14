package com.example.monitor;

import com.example.monitor.config.TrafficMonitorProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = {"com.example.monitor", "com.example.handlerapp"})
@EnableConfigurationProperties(TrafficMonitorProperties.class)
public class TrafficMonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrafficMonitorApplication.class, args);
    }
}
