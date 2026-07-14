package com.example.handlerapp.weather;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.weather.TemperatureReadingMessage;
import org.springframework.stereotype.Component;

@Component
public class TemperatureReadingMessageHandler implements MessageArrivedHandler<TemperatureReadingMessage> {

    @Override
    public String interfaceName() {
        return "Weather Interface";
    }

    @Override
    public String messageType() {
        return "TemperatureReading";
    }

    @Override
    public void onMessageArrived(TemperatureReadingMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        // TODO: decide what to do when a TemperatureReading message arrives, e.g.:
        // if (destinationConfig != null) {
        //     replySender.reply(message, destinationConfig.host(), destinationConfig.port());
        // }
    }
}
