package com.example.handlerapp.fruit;

import com.example.handlercore.IncomingMessage;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import org.springframework.stereotype.Component;

@Component
public class BananaMessageHandler implements MessageArrivedHandler {

    @Override
    public String interfaceName() {
        return "Fruit Interface";
    }

    @Override
    public String messageType() {
        return "Banana";
    }

    @Override
    public void onMessageArrived(IncomingMessage message, ReplySender replySender) {
        // TODO: decide what to do when a Banana message arrives, e.g.:
        // BananaMessage banana = new BananaMessage(
        //         (String) message.body().get("color"),
        //         ((Number) message.body().get("weight")).doubleValue()
        // );
        // replySender.reply("Fruit Interface", "Orange", Map.of(...), message.remoteHost(), message.remotePort());
    }
}
