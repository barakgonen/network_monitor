package com.example.monitor;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureObservability
class ActuatorEndToEndIT extends AbstractIntegrationTestBase {

    @Test
    void health_returnsUp() {
        ResponseEntity<String> response = restTemplate.getForEntity(httpUrl("/actuator/health"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }

    @Test
    void metrics_messagesReceivedIsQueryable() throws Exception {
        sendUdp(fruitPort, TestProtocolPayloads.ping(1));
        awaitStoreContains(m -> "Ping".equals(m.messageType()));

        ResponseEntity<String> response = restTemplate.getForEntity(
                httpUrl("/actuator/metrics/network_monitor.messages.received"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("network_monitor.messages.received");
    }

    @Test
    void prometheus_endpointIsExposed() {
        ResponseEntity<String> response = restTemplate.getForEntity(httpUrl("/actuator/prometheus"), String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
