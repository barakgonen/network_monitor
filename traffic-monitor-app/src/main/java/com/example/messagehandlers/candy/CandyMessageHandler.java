package com.example.messagehandlers.candy;

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
        // Candy is a dedicated-port (messageOwnsHeader) interface, so unlike the legacy-envelope
        // examples above, a reply here should NOT go through MonitorPayloadFactory.create(...)
        // (that always legacy-envelope-wraps) - encode via the interface's own message class
        // convention instead (see CLAUDE.md's reflective codec convention) and pass the raw bytes:
        // if (destinationConfig != null) {
        //     replySender.reply(rawEncodedBytes, destinationConfig.host(), destinationConfig.port(), destinationConfig.transport());
        // }
    }
}
