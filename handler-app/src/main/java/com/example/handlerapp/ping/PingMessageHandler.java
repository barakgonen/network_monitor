package com.example.handlerapp.ping;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;
import org.springframework.stereotype.Component;

@Component
public class PingMessageHandler implements MessageArrivedHandler<PingMessage> {

    @Override
    public String interfaceName() {
        return "Ping Interface";
    }

    @Override
    public String messageType() {
        return "Ping";
    }

    @Override
    public void onMessageArrived(PingMessage message, ReplySender replySender, DestinationConfig destinationConfig) {
        if (destinationConfig != null) {
            replySender.reply(new PongMessage(message.sequence()), destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        }
    }
}
