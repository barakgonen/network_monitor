package com.example.handlerapp.fruit;

import com.example.handlercore.IncomingMessage;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.OrangeMessage;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class OrangeMessageHandler implements MessageArrivedHandler {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Orange";
    }

    @Override
    public void onMessageArrived(IncomingMessage message, ReplySender replySender) {
        OrangeMessage orange = new OrangeMessage(
                (String) message.body().get("sourceFarm"),
                FruitFreshness.fromWireName((String) message.body().get("freshness"))
        );

        if (orange.freshness() == FruitFreshness.NOT_FRESH) {
            replySender.reply(
                    "Fruit Interface",
                    "Banana",
                    Map.of("color", "yellow", "weight", 100.0),
                    message.remoteHost(),
                    message.remotePort()
            );
        }
    }
}
