//package com.example.monitor.callback.events;
//
//import com.example.monitor.callback.EventDispatcher;
//
//import java.util.ServiceLoader;
//
//public class EventDispatcherFactory {
//
//    public EventDispatcher create() {
//        EventDispatcher.Builder builder = EventDispatcher.builder();
//
//        ServiceLoader<EventModule> modules =
//                ServiceLoader.load(EventModule.class);
//
//        for (EventModule module : modules) {
//            builder.registerModule(module);
//        }
//
//        return builder.build();
//    }
//}
