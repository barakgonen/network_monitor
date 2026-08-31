package com.example.publisher.config;

import com.example.monitor.publishing.RestOperationInvoker;
import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.UdpMessagePublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

/**
 * Explicit {@code @Bean} wiring for traffic-monitor-app-core's send-mechanics classes
 * ({@code com.example.monitor.publishing}) - {@code MeterRegistry} comes from Spring Boot's own
 * Micrometer autoconfiguration (present transitively via traffic-monitor-app-core's
 * spring-boot-starter-actuator dependency), {@code ObjectMapper} from its Jackson autoconfiguration.
 */
@Configuration
public class SendingBeans {

    @Bean
    public UdpMessagePublisher udpMessagePublisher(MeterRegistry meterRegistry) {
        return new UdpMessagePublisher(meterRegistry);
    }

    @Bean
    public TcpMessagePublisher tcpMessagePublisher(MeterRegistry meterRegistry) {
        return new TcpMessagePublisher(meterRegistry);
    }

    @Bean
    public RestOperationInvoker restOperationInvoker(ObjectMapper objectMapper) {
        return new RestOperationInvoker(objectMapper);
    }
}
