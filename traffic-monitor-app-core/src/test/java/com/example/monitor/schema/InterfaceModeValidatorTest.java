package com.example.monitor.schema;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceModeValidatorTest {

    @Test
    void validate_serverModeWithoutHost_passes() {
        assertThatCode(() -> InterfaceModeValidator.validate("SERVER", "UDP", null, "interface fruit"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_clientModeWithTcpAndHost_passes() {
        assertThatCode(() -> InterfaceModeValidator.validate("CLIENT", "TCP", "remote-host", "interface fruit"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_clientModeIsCaseInsensitive() {
        assertThatCode(() -> InterfaceModeValidator.validate("client", "tcp", "remote-host", "interface fruit"))
                .doesNotThrowAnyException();
    }

    @Test
    void validate_invalidModeString_throws() {
        assertThatThrownBy(() -> InterfaceModeValidator.validate("BOGUS", "TCP", "remote-host", "interface fruit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SERVER or CLIENT");
    }

    @Test
    void validate_nullMode_throws() {
        assertThatThrownBy(() -> InterfaceModeValidator.validate(null, "TCP", "remote-host", "interface fruit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SERVER or CLIENT");
    }

    @Test
    void validate_clientModeWithUdpProtocol_throws() {
        assertThatThrownBy(() -> InterfaceModeValidator.validate("CLIENT", "UDP", "remote-host", "interface fruit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol=TCP");
    }

    @Test
    void validate_clientModeWithBlankHost_throws() {
        assertThatThrownBy(() -> InterfaceModeValidator.validate("CLIENT", "TCP", "  ", "interface fruit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank host");
    }

    @Test
    void validate_clientModeWithNullHost_throws() {
        assertThatThrownBy(() -> InterfaceModeValidator.validate("CLIENT", "TCP", null, "interface fruit"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank host");
    }
}
