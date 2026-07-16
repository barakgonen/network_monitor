package com.example.handlerapp.fruit;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.fruit.BananaMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class BananaMessageHandlerTest {

    @Mock
    private ReplySender replySender;

    private final BananaMessageHandler handler = new BananaMessageHandler();

    @Test
    void interfaceName_and_messageType_returnFruitInterfaceAndBanana() {
        assertThat(handler.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(handler.messageType()).isEqualTo("Banana");
    }

    @Test
    void onMessageArrived_isNoOpRegardlessOfInputs() {
        handler.onMessageArrived(new BananaMessage("yellow", 1.0), replySender, new DestinationConfig("localhost", 7001, "UDP"));
        handler.onMessageArrived(new BananaMessage("green", 2.0), replySender, null);

        verifyNoInteractions(replySender);
    }
}
