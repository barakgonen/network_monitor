package com.example.handlerapp.weather;

import com.example.handlercore.IncomingMessage;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import org.springframework.stereotype.Component;

@Component
public class TemperatureReadingMessageHandler implements MessageArrivedHandler {

    @Override
    public String interfaceName() {
        return "Weather Interface";
    }

    @Override
    public String messageType() {
        return "TemperatureReading";
    }

    @Override
    public void onMessageArrived(IncomingMessage message, ReplySender replySender) {
        // TODO: decide what to do when a TemperatureReading message arrives, e.g.:
        // TemperatureReadingMessage reading = new TemperatureReadingMessage(
        //         (String) message.body().get("stationId"),
        //         ((Number) message.body().get("temperatureCelsius")).doubleValue(),
        //         WeatherCondition.fromWireName((String) message.body().get("condition"))
        // );
        // replySender.reply("Weather Interface", "TemperatureReading", Map.of(...), message.remoteHost(), message.remotePort());
    }
}
