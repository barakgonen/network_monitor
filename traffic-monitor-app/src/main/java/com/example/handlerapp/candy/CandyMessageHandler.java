package com.example.handlerapp.candy;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.candy.CandyMessage;
import org.springframework.stereotype.Component;

@Component
public class CandyMessageHandler implements MessageArrivedHandler<CandyMessage> {

    @Override
    public String interfaceName() {
        return "Candy Interface";
    }

    @Override
    public String messageType() {
        return "Candy";
    }

    @Override
    public void onMessageArrived(CandyMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        // TODO: decide what to do when a Candy message arrives, e.g.:
        // if (destinationConfig != null) {
        //     replySender.reply(message, destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        // }
    }
}
