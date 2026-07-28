package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InterfaceRuntimeStateTest {

    @Test
    void initialState_isNotListeningWithZeroCounts() {
        InterfaceRuntimeState state = new InterfaceRuntimeState(new InterfaceConfig());

        assertThat(state.isListening()).isFalse();
        assertThat(state.receivedCount()).isZero();
        assertThat(state.parseErrorCount()).isZero();
        assertThat(state.lastObservedAt()).isNull();
    }

    @Test
    void recordObserved_incrementsReceivedCountAndSetsTimestamp() {
        InterfaceRuntimeState state = new InterfaceRuntimeState(new InterfaceConfig());

        state.recordObserved(false);

        assertThat(state.receivedCount()).isEqualTo(1);
        assertThat(state.parseErrorCount()).isZero();
        assertThat(state.lastObservedAt()).isNotNull();
    }

    @Test
    void recordObserved_withParseError_incrementsBothCounters() {
        InterfaceRuntimeState state = new InterfaceRuntimeState(new InterfaceConfig());

        state.recordObserved(true);

        assertThat(state.receivedCount()).isEqualTo(1);
        assertThat(state.parseErrorCount()).isEqualTo(1);
    }

    @Test
    void configure_whileNotListening_updatesPortAndProtocol() {
        InterfaceConfig config = new InterfaceConfig();
        config.setPort(5001);
        config.setProtocol("UDP");
        InterfaceRuntimeState state = new InterfaceRuntimeState(config);

        state.configure(6001, "TCP");

        assertThat(config.getPort()).isEqualTo(6001);
        assertThat(config.getProtocol()).isEqualTo("TCP");
    }

    @Test
    void configure_whileListening_throwsIllegalStateException() {
        InterfaceConfig config = new InterfaceConfig();
        config.setKey("fruit");
        config.setPort(5001);
        InterfaceRuntimeState state = new InterfaceRuntimeState(config);
        state.setListening(true);

        assertThatThrownBy(() -> state.configure(6001, "TCP"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fruit");
    }
}
