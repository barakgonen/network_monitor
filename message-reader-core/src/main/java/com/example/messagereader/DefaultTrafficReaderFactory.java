package com.example.messagereader;

import com.example.messagereader.api.TrafficInterfaceDefinition;
import com.example.messagereader.api.TrafficMessageHandler;
import com.example.messagereader.api.TrafficReader;
import com.example.messagereader.api.TrafficReaderFactory;
import com.example.messagereader.transport.DefaultTransportSubscriberFactory;
import com.example.messagereader.transport.TransportSubscriber;
import com.example.messagereader.transport.TransportSubscriberFactory;

public class DefaultTrafficReaderFactory implements TrafficReaderFactory {
    private final TransportSubscriberFactory subscriberFactory;

    public DefaultTrafficReaderFactory() {
        this(new DefaultTransportSubscriberFactory());
    }

    public DefaultTrafficReaderFactory(TransportSubscriberFactory subscriberFactory) {
        this.subscriberFactory = subscriberFactory;
    }

    @Override
    public TrafficReader createReader(
            TrafficInterfaceDefinition definition,
            int bufferSizeBytes,
            TrafficMessageHandler handler
    ) {
        DefaultTrafficReader[] holder = new DefaultTrafficReader[1];

        TransportSubscriber subscriber = subscriberFactory.createSubscriber(
                definition.getProtocol(),
                definition.getPort(),
                bufferSizeBytes,
                packet -> holder[0].handleRawPacket(packet)
        );

        holder[0] = new DefaultTrafficReader(definition, handler, subscriber);
        return holder[0];
    }
}
