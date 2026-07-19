package com.example.monitor.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InterfaceConfigTest {

    @Test
    void defaults_matchLegacySharedPortBehavior() {
        InterfaceConfig config = new InterfaceConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getProtocol()).isEqualTo("UDP");
        assertThat(config.getPort()).isNull();
        assertThat(config.hasDedicatedPort()).isFalse();
        assertThat(config.getByteOrder()).isEqualTo("BIG_ENDIAN");
        assertThat(config.getHeaderType()).isEqualTo("com.example.schemacore.DefaultEnvelopeHeader");
        assertThat(config.getOpcodeFieldName()).isEqualTo("opcode");
    }

    @Test
    void hasDedicatedPort_isTrue_whenPortConfigured() {
        InterfaceConfig config = new InterfaceConfig();
        config.setPort(5050);

        assertThat(config.hasDedicatedPort()).isTrue();
    }
}
