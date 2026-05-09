package com.example.messagereader;

import com.example.messagereader.api.ParsedTrafficMessage;
import com.example.messagereader.api.TrafficInterfaceDefinition;
import com.example.messagereader.api.TrafficMessageHandler;
import com.example.messagereader.api.TrafficReader;
import com.example.messagereader.reflection.ReflectionTrafficMessageParser;
import com.example.messagereader.transport.TransportSubscriber;

public class DefaultTrafficReader implements TrafficReader {
    private final TrafficInterfaceDefinition definition;
    private final TrafficMessageHandler handler;
    private final TransportSubscriber subscriber;
    private final ReflectionTrafficMessageParser parser = new ReflectionTrafficMessageParser();

    public DefaultTrafficReader(
            TrafficInterfaceDefinition definition,
            TrafficMessageHandler handler,
            TransportSubscriber subscriber
    ) {
        this.definition = definition;
        this.handler = handler;
        this.subscriber = subscriber;
    }

    @Override
    public void start() {
        subscriber.start();
    }

    @Override
    public void stop() {
        subscriber.stop();
    }

    @Override
    public boolean isRunning() {
        return subscriber.isRunning();
    }

    public void handleRawPacket(com.example.messagereader.api.RawTrafficPacket packet) {
        ParsedTrafficMessage parsed = parser.parse(packet, definition);
        handler.onMessage(parsed);
    }
}
