package com.example.monitor;

import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UdpIngestionEndToEndIT extends AbstractIntegrationTestBase {

    @Test
    void sendingValidOrangePayload_landsInRecentMessageStore() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.orange("Sunny Acres", (byte) 1));

        ObservedMessage message = awaitStoreContains(m -> "Orange".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("sourceFarm")).isEqualTo("Sunny Acres");
        assertThat(message.body().get("freshness")).isEqualTo("very_fresh");
    }

    @Test
    void sendingValidBananaPayload_landsInStore() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.banana("yellow", 123.5));

        ObservedMessage message = awaitStoreContains(m -> "Banana".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("color")).isEqualTo("yellow");
        assertThat(message.body().get("weight")).isEqualTo(123.5);
    }

    @Test
    void sendingValidTemperatureReadingPayload_toWeatherPort_landsInStore() throws Exception {
        sendUdp(weatherPort, TestProtocolPayloads.temperatureReading("station-1", 21.5, (byte) 1));

        ObservedMessage message = awaitStoreContains(m -> "TemperatureReading".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Weather Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("stationId")).isEqualTo("station-1");
        assertThat(message.body().get("temperatureCelsius")).isEqualTo(21.5);
        assertThat(message.body().get("condition")).isEqualTo("sunny");
    }

    @Test
    void sendingValidPingPayload_toFruitPort_landsInStoreWithPingInterfaceName() throws Exception {
        // Ping has no dedicated UDP listener port configured today; ingestion decode is
        // opcode-driven, not port-driven, so it is sent to (and understood on) the fruit port,
        // matching what the tester scenario config does.
        sendUdp(fruitPort, TestProtocolPayloads.ping(42));

        ObservedMessage message = awaitStoreContains(m -> "Ping".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Ping Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("sequence")).isEqualTo(42);
    }

    @Test
    void sendingMalformedPayload_tooShortForHeader_landsInStoreWithParseError() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.tooShortForHeader());

        ObservedMessage message = awaitStoreContains(m -> m.parseError() != null && m.payloadSizeBytes() == 3);

        assertThat(message.interfaceName()).isEqualTo("Unknown");
        assertThat(message.messageType()).isEqualTo("Unknown");
    }

    @Test
    void sendingPayloadWithUnknownOpcode_landsInStoreWithParseError() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.rawHeaderOnly(9999, 0));

        ObservedMessage message = awaitStoreContains(m -> m.parseError() != null && "Unknown".equals(m.interfaceName()));

        assertThat(message.parseError()).contains("Unknown opcode");
    }

    @Test
    void sendingPayloadWithBodyLengthMismatch_landsInStoreWithParseError() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.withBodyLengthMismatch());

        ObservedMessage message = awaitStoreContains(m -> m.parseError() != null && m.payloadSizeBytes() > 0);

        assertThat(message.parseError()).contains("Invalid bodyLength");
    }

    @Test
    void multipleMessagesSentInSequence_allLandInStoreNewestFirst() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.orange("farm-a", (byte) 1));
        Thread.sleep(50);
        sendUdp(fruitPort, TestProtocolPayloads.banana("green", 1.0));
        Thread.sleep(50);
        sendUdp(fruitPort, TestProtocolPayloads.ping(7));

        awaitStoreContains(m -> "Ping".equals(m.messageType()) && Integer.valueOf(7).equals(m.body().get("sequence")));

        var recent = recentMessageStore.recent();
        int pingIndex = indexOfMessageType(recent, "Ping");
        int bananaIndex = indexOfMessageType(recent, "Banana");
        int orangeIndex = indexOfMessageType(recent, "Orange");

        assertThat(pingIndex).isLessThan(bananaIndex);
        assertThat(bananaIndex).isLessThan(orangeIndex);
    }

    @Test
    void messagesReceivedMetric_incrementsForUdpTraffic() throws Exception {
        double before = meterRegistry.counter("network_monitor.messages.received",
                "transport", "UDP", "interfaceName", "Fruit Interface", "parseError", "false").count();

        sendUdp(fruitPort, TestProtocolPayloads.orange("udp-metrics-farm", (byte) 1));
        awaitStoreContains(m -> "Orange".equals(m.messageType()) && "udp-metrics-farm".equals(m.body().get("sourceFarm")));

        double after = meterRegistry.counter("network_monitor.messages.received",
                "transport", "UDP", "interfaceName", "Fruit Interface", "parseError", "false").count();

        assertThat(after).isEqualTo(before + 1);
    }

    private static int indexOfMessageType(java.util.List<ObservedMessage> messages, String messageType) {
        for (int i = 0; i < messages.size(); i++) {
            if (messageType.equals(messages.get(i).messageType())) {
                return i;
            }
        }
        throw new AssertionError("No message of type " + messageType + " found");
    }
}
