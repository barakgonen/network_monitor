package com.example.handlerapp.fruit;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.fruit.BananaMessage;
import org.springframework.stereotype.Component;

@Component
public class BananaMessageHandler implements MessageArrivedHandler<BananaMessage> {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Banana";
    }

    @Override
    public void onMessageArrived(BananaMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        // TODO: decide what to do when a Banana message arrives, e.g.:
        // if (destinationConfig != null) {
        //     replySender.reply(new OrangeMessage("some-farm", FruitFreshness.VERY_FRESH),
        //             destinationConfig.host(), destinationConfig.port());
        // }
    }
}
