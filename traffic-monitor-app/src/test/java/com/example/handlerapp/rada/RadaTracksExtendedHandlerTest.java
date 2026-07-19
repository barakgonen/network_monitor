package com.example.handlerapp.rada;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.rada.messages.RadaTracksExtended;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RadaTracksExtendedHandlerTest {

    @Mock
    private ReplySender replySender;

    private final RadaTracksExtendedHandler handler = new RadaTracksExtendedHandler();

    @Test
    void interfaceName_and_messageType_returnRadaInterfaceAndRadaTracksExtended() {
        assertThat(handler.interfaceName()).isEqualTo("Rada Interface");
        assertThat(handler.messageType()).isEqualTo("RadaTracksExtended");
    }

    @Test
    void onMessageArrived_isNoOpRegardlessOfInputs() {
        handler.onMessageArrived(new RadaTracksExtended(), replySender, new DestinationConfig("localhost", 7001, "UDP"));
        handler.onMessageArrived(new RadaTracksExtended(), replySender, null);

        verifyNoInteractions(replySender);
    }
}
