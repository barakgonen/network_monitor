package com.example.messagehandlers.fruit;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.monitor.publishing.MonitorPayloadFactory;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.OrangeMessage;
import org.springframework.stereotype.Component;

@Component
public class OrangeMessageHandler implements MessageArrivedHandler<OrangeMessage> {
    private final MonitorPayloadFactory payloadFactory;

    public OrangeMessageHandler(MonitorPayloadFactory payloadFactory) {
        this.payloadFactory = payloadFactory;
    }

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
            BananaMessage banana = new BananaMessage("yellow", 100.0);
            replySender.reply(payloadFactory.create(banana), destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        }
    }
}
