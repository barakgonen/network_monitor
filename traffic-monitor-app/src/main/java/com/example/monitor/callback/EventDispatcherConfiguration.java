//package com.example.monitor.callback;
//
//import com.example.monitor.callback.events.DomainEvent;
//import com.example.monitor.callback.events.EventModule;
//import com.example.monitor.callback.events.TypedEventHandler;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.List;
//
//@Configuration
//public class EventDispatcherConfiguration {
//
//    @Bean
//    public EventDispatcher eventDispatcher(List<TypedEventHandler<? extends DomainEvent>> handlers,
//                                           List<EventModule> modules) {
//        EventDispatcher.Builder builder = EventDispatcher.builder();
//
//        for (EventModule module : modules) {
//            builder.registerModule(module);
//        }
//
//        for (TypedEventHandler<? extends DomainEvent> handler : handlers) {
//            registerHandler(builder, handler);
//        }
//
//        return builder.build();
//    }
//
//    private static <T extends DomainEvent> void registerHandler(
//            EventDispatcher.Builder builder,
//            TypedEventHandler<T> handler
//    ) {
//        builder.registerHandler(handler);
//    }
//}
