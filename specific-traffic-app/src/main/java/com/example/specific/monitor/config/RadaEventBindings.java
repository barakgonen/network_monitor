package com.example.specific.monitor.config;

import com.example.monitor.callback.EventBindingConfigurer;
import com.example.monitor.callback.EventBindingRegistry;
import com.example.schemas.rada.messages.RadaTracksExtended;
import com.example.specific.monitor.handlers.RadaTracksExtendedHandler;
import org.springframework.stereotype.Component;

@Component
public class RadaEventBindings implements EventBindingConfigurer {

    private final RadaTracksExtendedHandler radaTracksExtendedHandler;

    public RadaEventBindings(RadaTracksExtendedHandler radaTracksExtendedHandler) {
        this.radaTracksExtendedHandler = radaTracksExtendedHandler;
    }

    @Override
    public void configure(EventBindingRegistry registry) {
        registry.bind(
                RadaTracksExtended.class,
                "rada-tracks-extended",
                radaTracksExtendedHandler
        );
    }
}
