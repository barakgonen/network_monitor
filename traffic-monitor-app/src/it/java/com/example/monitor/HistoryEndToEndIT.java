package com.example.monitor;

import com.example.monitor.api.HistoryResponse;
import com.example.monitor.model.ObservedMessage;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class HistoryEndToEndIT extends AbstractIntegrationTestBase {

    @Test
    void sendingValidOrangePayload_landsInDurableHistory() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.orange("Durable Acres", (byte) 1));

        ObservedMessage message = awaitHistoryContains(m -> "Orange".equals(m.messageType()));

        assertThat(message.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(message.parseError()).isNull();
        assertThat(message.body().get("sourceFarm")).isEqualTo("Durable Acres");
    }

    @Test
    void sendingValidOrangePayload_isQueryableViaHistoryRestEndpoint() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.orange("REST Acres", (byte) 1));
        awaitHistoryContains(m -> "Orange".equals(m.messageType()) && "REST Acres".equals(m.body().get("sourceFarm")));

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            HistoryResponse response = restTemplate.getForObject(
                    httpUrl("/api/messages/history?messageType=Orange&limit=500"),
                    HistoryResponse.class);

            assertThat(response).isNotNull();
            assertThat(response.items()).anyMatch(m -> "REST Acres".equals(m.body().get("sourceFarm")));
        });
    }

    @Test
    void sendingMalformedPayload_alsoLandsInHistoryWithParseError() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.tooShortForHeader());

        ObservedMessage message = awaitHistoryContains(m -> m.parseError() != null && m.payloadSizeBytes() == 3);

        assertThat(message.interfaceName()).isEqualTo("Unknown");
        assertThat(message.messageType()).isEqualTo("Unknown");
    }
}
