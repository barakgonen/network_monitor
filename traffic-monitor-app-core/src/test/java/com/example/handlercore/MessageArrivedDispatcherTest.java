package com.example.handlercore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MessageArrivedDispatcherTest {

    @Mock
    private ReplySender replySender;

    @SuppressWarnings("unchecked")
    @Test
    void dispatch_whenHandlerRegistered_invokesOnMessageArrivedWithMessageAndDestinationConfig() {
        MessageArrivedHandler<Object> handler = org.mockito.Mockito.mock(MessageArrivedHandler.class);
        org.mockito.Mockito.when(handler.interfaceName()).thenReturn("Fruit Interface");
        org.mockito.Mockito.when(handler.messageType()).thenReturn("Orange");

        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of(handler));
        MessageArrivedDispatcher dispatcher = new MessageArrivedDispatcher(registry, replySender);

        Object message = new Object();
        DestinationConfig destinationConfig = new DestinationConfig("localhost", 7001, "UDP");

        dispatcher.dispatch("Fruit Interface", "Orange", message, destinationConfig);

        ArgumentCaptor<Object> messageCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<DestinationConfig> destinationCaptor = ArgumentCaptor.forClass(DestinationConfig.class);
        verify(handler).onMessageArrived(messageCaptor.capture(), org.mockito.Mockito.eq(replySender), destinationCaptor.capture());

        assertThat(messageCaptor.getValue()).isSameAs(message);
        assertThat(destinationCaptor.getValue()).isEqualTo(destinationConfig);
    }

    @Test
    void dispatch_whenNoHandlerRegistered_doesNothingSilently() {
        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of());
        MessageArrivedDispatcher dispatcher = new MessageArrivedDispatcher(registry, replySender);

        dispatcher.dispatch("Fruit Interface", "Orange", new Object(), null);

        verifyNoInteractions(replySender);
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatch_passesThroughNullDestinationConfig() {
        MessageArrivedHandler<Object> handler = org.mockito.Mockito.mock(MessageArrivedHandler.class);
        org.mockito.Mockito.when(handler.interfaceName()).thenReturn("Ping Interface");
        org.mockito.Mockito.when(handler.messageType()).thenReturn("Ping");

        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of(handler));
        MessageArrivedDispatcher dispatcher = new MessageArrivedDispatcher(registry, replySender);

        Object message = new Object();
        dispatcher.dispatch("Ping Interface", "Ping", message, null);

        verify(handler).onMessageArrived(message, replySender, null);
    }

    @SuppressWarnings("unchecked")
    @Test
    void dispatch_whenMultipleHandlersRegisteredForDifferentKeys_onlyMatchingOneInvoked() {
        MessageArrivedHandler<Object> orangeHandler = org.mockito.Mockito.mock(MessageArrivedHandler.class);
        org.mockito.Mockito.when(orangeHandler.interfaceName()).thenReturn("Fruit Interface");
        org.mockito.Mockito.when(orangeHandler.messageType()).thenReturn("Orange");

        MessageArrivedHandler<Object> pingHandler = org.mockito.Mockito.mock(MessageArrivedHandler.class);
        org.mockito.Mockito.when(pingHandler.interfaceName()).thenReturn("Ping Interface");
        org.mockito.Mockito.when(pingHandler.messageType()).thenReturn("Ping");

        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of(orangeHandler, pingHandler));
        MessageArrivedDispatcher dispatcher = new MessageArrivedDispatcher(registry, replySender);

        Object message = new Object();
        dispatcher.dispatch("Ping Interface", "Ping", message, null);

        verify(pingHandler).onMessageArrived(message, replySender, null);
        verify(orangeHandler, never()).onMessageArrived(org.mockito.Mockito.any(), org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }
}
