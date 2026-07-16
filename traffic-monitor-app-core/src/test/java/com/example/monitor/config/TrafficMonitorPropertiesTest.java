package com.example.monitor.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficMonitorPropertiesTest {

    @Test
    void defaults_matchExpectedValues() {
        TrafficMonitorProperties properties = new TrafficMonitorProperties();

        assertThat(properties.getUdp().isEnabled()).isTrue();
        assertThat(properties.getUdp().getFruitPort()).isEqualTo(5001);
        assertThat(properties.getUdp().getWeatherPort()).isEqualTo(5003);
        assertThat(properties.getUdp().getBufferSizeBytes()).isEqualTo(65507);
        assertThat(properties.getTcp().isEnabled()).isTrue();
        assertThat(properties.getTcp().getFruitPort()).isEqualTo(5001);
        assertThat(properties.getTcp().getWeatherPort()).isEqualTo(5003);
        assertThat(properties.getTcp().getMaxBodyLengthBytes()).isEqualTo(65507);
        assertThat(properties.getStore().getMaxSize()).isEqualTo(500);
    }
}
