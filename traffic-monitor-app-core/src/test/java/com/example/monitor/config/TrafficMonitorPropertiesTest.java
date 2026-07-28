package com.example.monitor.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficMonitorPropertiesTest {

    @Test
    void defaults_matchExpectedValues() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();

        assertThat(properties.getUdp().getBufferSizeBytes()).isEqualTo(65507);
        assertThat(properties.getTcp().getMaxBodyLengthBytes()).isEqualTo(65507);
        assertThat(properties.getStore().getMaxSize()).isEqualTo(500);
    }
}
