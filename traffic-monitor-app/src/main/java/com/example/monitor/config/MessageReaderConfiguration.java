package com.example.monitor.config;

import com.example.messagereader.DefaultTrafficReaderFactory;
import com.example.messagereader.api.TrafficPublisher;
import com.example.messagereader.api.TrafficReaderFactory;
import com.example.messagereader.publisher.DefaultTrafficPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MessageReaderConfiguration {
    @Bean
    public TrafficReaderFactory trafficReaderFactory() {
        return new DefaultTrafficReaderFactory();
    }

    @Bean
    public TrafficPublisher trafficPublisher() {
        return new DefaultTrafficPublisher();
    }
}
