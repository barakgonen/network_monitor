package com.example.schemas.ping;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PingOpcodesTest {

    @Test
    void messageType_forKnownOpcodes_returnsExpectedNames() {
        assertThat(PingOpcodes.messageType(PingOpcodes.PING)).isEqualTo("Ping");
        assertThat(PingOpcodes.messageType(PingOpcodes.PONG)).isEqualTo("Pong");
    }

    @Test
    void messageType_forUnknownOpcode_returnsUnknown() {
        assertThat(PingOpcodes.messageType(-1)).isEqualTo("Unknown");
    }
}
