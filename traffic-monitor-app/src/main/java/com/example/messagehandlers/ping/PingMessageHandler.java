package com.example.messagehandlers.ping;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.monitor.publishing.MonitorPayloadFactory;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;
import org.springframework.stereotype.Component;

@Component
public class PingMessageHandler implements MessageArrivedHandler<PingMessage> {
    private final MonitorPayloadFactory payloadFactory;

    public PingMessageHandler(MonitorPayloadFactory payloadFactory) {
        this.payloadFactory = payloadFactory;
    }

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
            PongMessage pong = new PongMessage(message.sequence());
            replySender.reply(payloadFactory.create(pong), destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        }
    }
}
