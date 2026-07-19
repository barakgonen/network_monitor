package com.example.handlerapp.ping;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.ping.PingMessage;
import com.example.schemas.ping.PongMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PingMessageHandlerTest {

    @Mock
    private ReplySender replySender;

    private final PingMessageHandler handler = new PingMessageHandler();

    @Test
    void interfaceName_and_messageType_returnPingInterfaceAndPing() {
        assertThat(handler.interfaceName()).isEqualTo("Ping Interface");
        assertThat(handler.messageType()).isEqualTo("Ping");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, -1, Integer.MAX_VALUE})
    void onMessageArrived_whenDestinationConfigPresent_repliesWithPongEchoingSameSequence(int sequence) {
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "UDP");

        handler.onMessageArrived(new PingMessage(sequence), replySender, destinationConfig);

        verify(replySender).reply(new PongMessage(sequence), "localhost", 7001, "UDP");
    }

    @Test
    void onMessageArrived_whenDestinationConfigIsTcp_repliesWithTcpTransport() {
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "TCP");

        handler.onMessageArrived(new PingMessage(42), replySender, destinationConfig);

        verify(replySender).reply(new PongMessage(42), "localhost", 7001, "TCP");
    }

    @Test
    void onMessageArrived_whenDestinationConfigNull_doesNotReply() {
        handler.onMessageArrived(new PingMessage(1), replySender, null);

        verifyNoInteractions(replySender);
    }
}
