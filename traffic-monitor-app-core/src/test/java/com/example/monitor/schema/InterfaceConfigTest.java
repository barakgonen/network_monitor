package com.example.monitor.schema;

import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceConfigTest {

    @Test
    void defaults_matchGenericEnvelopeBehavior() {
        InterfaceConfig config = new InterfaceConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getProtocol()).isEqualTo("UDP");
        assertThat(config.getPort()).isNull();
        assertThat(config.hasDedicatedPort()).isFalse();
        assertThat(config.getByteOrder()).isEqualTo("BIG_ENDIAN");
        assertThat(config.getHeaderType()).isEqualTo("com.example.schemacore.envelope.DefaultEnvelopeHeader");
        assertThat(config.getOpcodeFieldName()).isEqualTo("opcode");
        assertThat(config.isMessageOwnsHeader()).isFalse();
        assertThat(config.getBodyLengthFieldName()).isEqualTo("bodyLength");
        assertThat(config.getMode()).isEqualTo("SERVER");
        assertThat(config.getHost()).isNull();
    }

    @Test
    void hasDedicatedPort_isTrue_whenPortConfigured() {
        InterfaceConfig config = new InterfaceConfig();
        config.setPort(5050);

        assertThat(config.hasDedicatedPort()).isTrue();
    }

    @Test
    void resolveByteOrder_defaultsToBigEndian() {
        InterfaceConfig config = new InterfaceConfig();

        assertThat(config.resolveByteOrder()).isEqualTo(ByteOrder.BIG_ENDIAN);
    }

    @Test
    void resolveByteOrder_reflectsLittleEndianOverride() {
        InterfaceConfig config = new InterfaceConfig();
        config.setByteOrder("LITTLE_ENDIAN");

        assertThat(config.resolveByteOrder()).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    }

    @Test
    void resolveByteOrder_failsFast_onInvalidValue() {
        InterfaceConfig config = new InterfaceConfig();
        config.setKey("stub");
        config.setByteOrder("MIDDLE_ENDIAN");

        assertThatThrownBy(config::resolveByteOrder)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MIDDLE_ENDIAN")
                .hasMessageContaining("interface stub")
                .hasMessageContaining("BIG_ENDIAN or LITTLE_ENDIAN");
    }
}
