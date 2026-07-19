package com.example.monitor.interfaces;

import com.example.monitor.schema.InterfaceConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
