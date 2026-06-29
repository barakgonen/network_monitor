package com.example.specific.monitor.handlers;

import com.example.monitor.callback.MessageHandler;
import com.example.schemas.rada.messages.RadaTracksExtended;
import org.springframework.stereotype.Component;

@Component
public class RadaTracksExtendedHandler implements MessageHandler<RadaTracksExtended> {

    @Override
    public void handle(RadaTracksExtended message) {
        System.out.println("track received updated:");
    }
}
