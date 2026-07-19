package com.example.handlerapp.candy;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.candy.CandyMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CandyMessageHandlerTest {

    @Mock
    private ReplySender replySender;

    private final CandyMessageHandler handler = new CandyMessageHandler();

    @Test
    void interfaceName_and_messageType_returnCandyInterfaceAndCandy() {
        assertThat(handler.interfaceName()).isEqualTo("Candy Interface");
        assertThat(handler.messageType()).isEqualTo("Candy");
    }

    @Test
    void onMessageArrived_isNoOpRegardlessOfInputs() {
        handler.onMessageArrived(new CandyMessage("chocolate-bar", 250.5), replySender, new DestinationConfig("localhost", 7001, "UDP"));
        handler.onMessageArrived(new CandyMessage("gummy-bear", 15.5), replySender, null);

        verifyNoInteractions(replySender);
    }
}
