package com.example.handlercore;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageHandlerRegistryTest {

    @Test
    void constructor_withUniqueHandlers_indexesByInterfaceAndMessageType() {
        StubHandler orangeHandler = new StubHandler("Fruit Interface", "Orange");
        StubHandler pingHandler = new StubHandler("Ping Interface", "Ping");

        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of(orangeHandler, pingHandler));

        assertThat(registry.find("Fruit Interface", "Orange")).contains(orangeHandler);
        assertThat(registry.find("Ping Interface", "Ping")).contains(pingHandler);
    }

    @Test
    void constructor_withDuplicateInterfaceAndMessageTypeKey_throwsIllegalStateException() {
        StubHandler first = new StubHandler("Fruit Interface", "Orange");
        StubHandler second = new StubHandler("Fruit Interface", "Orange");

        assertThatThrownBy(() -> new MessageHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Fruit Interface::Orange");
    }

    @Test
    void constructor_withEmptyList_producesEmptyRegistry() {
        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of());

        assertThat(registry.find("Fruit Interface", "Orange")).isEmpty();
    }

    @Test
    void find_whenNoMatch_returnsEmptyOptional() {
        MessageHandlerRegistry registry = new MessageHandlerRegistry(
                List.of(new StubHandler("Fruit Interface", "Orange")));

        assertThat(registry.find("Fruit Interface", "Banana")).isEmpty();
    }

    @Test
    void find_withMatchingInterfaceAndType_returnsHandler() {
        StubHandler handler = new StubHandler("Weather Interface", "TemperatureReading");
        MessageHandlerRegistry registry = new MessageHandlerRegistry(List.of(handler));

        assertThat(registry.find("Weather Interface", "TemperatureReading")).contains(handler);
    }

    private record StubHandler(String interfaceName, String messageType) implements MessageArrivedHandler<Object> {
        @Override
        public void onMessageArrived(Object message, ReplySender replySender, DestinationConfig destinationConfig) {
            // no-op stub
        }
    }
}
