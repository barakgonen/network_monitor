package com.example.handlerapp.weather;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.weather.TemperatureReadingMessage;
import com.example.schemas.weather.WeatherCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TemperatureReadingMessageHandlerTest {

    @Mock
    private ReplySender replySender;

    private final TemperatureReadingMessageHandler handler = new TemperatureReadingMessageHandler();

    @Test
    void interfaceName_and_messageType_returnWeatherInterfaceAndTemperatureReading() {
        assertThat(handler.interfaceName()).isEqualTo("Weather Interface");
        assertThat(handler.messageType()).isEqualTo("TemperatureReading");
    }

    @Test
    void onMessageArrived_isNoOpRegardlessOfInputs() {
        TemperatureReadingMessage message = new TemperatureReadingMessage("station-1", 20.0, WeatherCondition.SUNNY);

        handler.onMessageArrived(message, replySender, new DestinationConfig("localhost", 7001, "UDP"));
        handler.onMessageArrived(message, replySender, null);

        verifyNoInteractions(replySender);
    }
}
