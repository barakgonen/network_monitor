package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProtocolResolveNamedTypeTest {

    private ProtocolConfig loadConfig() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/position-report-protocol.json")) {
            return Protocol.loadConfig(in);
        }
    }

    @Test
    void resolveNamedType_findsBuiltInType() throws Exception {
        ProtocolConfig cfg = loadConfig();

        Type<?> type = Protocol.resolveNamedType(cfg, "double64");

        assertThat(type.getSizeInBytes()).isEqualTo(8);
    }

    @Test
    void resolveNamedType_findsCustomRecordTypeNotUsedByAnyMessage() throws Exception {
        ProtocolConfig cfg = loadConfig();

        // "myPos" is declared under types: and used inside "complexPosition"/messages, but this
        // resolves it directly by name - the same thing a messageOwnsHeader interface's header
        // type resolution needs (SerdesHeaderDecoder), independent of any message referencing it.
        Type<?> type = Protocol.resolveNamedType(cfg, "myPos");

        assertThat(type).isInstanceOf(RecordType.class);
        assertThat(((RecordType) type).getFields()).containsKeys("lat", "lon", "alt");
    }

    @Test
    void resolveNamedType_withUnknownName_throws() throws Exception {
        ProtocolConfig cfg = loadConfig();

        assertThatThrownBy(() -> Protocol.resolveNamedType(cfg, "doesNotExist"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("doesNotExist");
    }
}
