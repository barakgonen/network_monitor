package com.example.handlerapp.rada;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.MessageArrivedHandler;
import com.example.handlercore.ReplySender;
import com.example.schemas.rada.messages.RadaTracksExtended;
import org.springframework.stereotype.Component;

@Component
public class RadaTracksExtendedHandler implements MessageArrivedHandler<RadaTracksExtended> {

    @Override
    public String interfaceName() {
        return "Rada Interface";
    }

    @Override
    public String messageType() {
        return "RadaTracksExtended";
    }

    @Override
    public void onMessageArrived(RadaTracksExtended message, ReplySender replySender, DestinationConfig destinationConfig) {
        // TODO: decide on downstream handling for track updates.
    }
}
