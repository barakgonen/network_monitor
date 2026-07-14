package com.example.handlerapp.fruit;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.OrangeMessage;
import org.springframework.stereotype.Component;

@Component
public class OrangeMessageHandler implements MessageArrivedHandler<OrangeMessage> {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Orange";
    }

    @Override
    public void onMessageArrived(OrangeMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        if (message.freshness() == FruitFreshness.NOT_FRESH && destinationConfig != null) {
            replySender.reply(new BananaMessage("yellow", 100.0), destinationConfig.host(), destinationConfig.port());
        }
    }
}
