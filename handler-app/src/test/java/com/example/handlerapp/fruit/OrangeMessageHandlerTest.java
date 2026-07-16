package com.example.handlerapp.fruit;

import com.example.handlercore.DestinationConfig;
import com.example.handlercore.ReplySender;
import com.example.schemas.fruit.BananaMessage;
import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.OrangeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrangeMessageHandlerTest {

    @Mock
    private ReplySender replySender;

    private final OrangeMessageHandler handler = new OrangeMessageHandler();

    @Test
    void interfaceName_and_messageType_returnFruitInterfaceAndOrange() {
        assertThat(handler.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(handler.messageType()).isEqualTo("Orange");
    }

    @Test
    void onMessageArrived_whenFreshnessNotFreshAndDestinationConfigPresent_repliesWithYellowBanana100Weight() {
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "UDP");

        handler.onMessageArrived(new OrangeMessage("farm", FruitFreshness.NOT_FRESH), replySender, destinationConfig);

        verify(replySender).reply(new BananaMessage("yellow", 100.0), "localhost", 7001, "UDP");
    }

    @Test
    void onMessageArrived_whenFreshnessNotFreshAndDestinationConfigIsTcp_repliesWithTcpTransport() {
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "TCP");

        handler.onMessageArrived(new OrangeMessage("farm", FruitFreshness.NOT_FRESH), replySender, destinationConfig);

        verify(replySender).reply(new BananaMessage("yellow", 100.0), "localhost", 7001, "TCP");
    }

    @ParameterizedTest
    @EnumSource(value = FruitFreshness.class, names = "NOT_FRESH", mode = EnumSource.Mode.EXCLUDE)
    void onMessageArrived_whenFreshnessNotNotFresh_doesNotReply(FruitFreshness freshness) {
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "UDP");

        handler.onMessageArrived(new OrangeMessage("farm", freshness), replySender, destinationConfig);

        verifyNoInteractions(replySender);
    }

    @Test
    void onMessageArrived_whenFreshnessNotFreshButDestinationConfigNull_doesNotReply() {
        handler.onMessageArrived(new OrangeMessage("farm", FruitFreshness.NOT_FRESH), replySender, null);

        verifyNoInteractions(replySender);
    }
}
