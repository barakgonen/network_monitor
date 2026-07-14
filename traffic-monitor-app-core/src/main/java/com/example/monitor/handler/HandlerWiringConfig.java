package com.example.monitor.handler;

import com.example.handlercore.MessageArrivedDispatcher;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.MessageHandlerRegistry;
import com.example.handlercore.ReplySender;
import com.example.monitor.publishing.MonitorPayloadFactory;
import com.example.monitor.publishing.UdpMessagePublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class HandlerWiringConfig {

    @Bean
    public ReplySender replySender(MonitorPayloadFactory payloadFactory, UdpMessagePublisher udpMessagePublisher) {
        return (message, host, port) -> {
            byte[] payload = payloadFactory.create(message);
            udpMessagePublisher.send(host, port, payload);
        };
    }

    @Bean
    public MessageHandlerRegistry messageHandlerRegistry(List<MessageArrivedHandler<?>> handlers) {
        return new MessageHandlerRegistry(handlers);
    }

    @Bean
    public MessageArrivedDispatcher messageArrivedDispatcher(MessageHandlerRegistry registry, ReplySender replySender) {
        return new MessageArrivedDispatcher(registry, replySender);
    }
}
