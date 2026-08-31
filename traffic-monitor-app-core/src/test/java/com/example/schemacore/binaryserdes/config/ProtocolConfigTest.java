package com.example.schemacore.binaryserdes.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolConfigTest {

    @Test
    void typeConfigShouldHoldFields() {
        TypeConfig tc = new TypeConfig();
        tc.name = "u16";
        tc.kind = "uint16";
        tc.length = null;

        assertThat(tc.name).isEqualTo("u16");
        assertThat(tc.kind).isEqualTo("uint16");
        assertThat(tc.length).isNull();
    }

    @Test
    void fieldAndMessageConfigShouldHoldFields() {
        FieldConfig fc = new FieldConfig();
        fc.name = "msgId";
        fc.type = "u16";

        MessageConfig mc = new MessageConfig();
        mc.name = "Header";
        mc.fields = List.of(fc);

        assertThat(fc.name).isEqualTo("msgId");
        assertThat(fc.type).isEqualTo("u16");
        assertThat(mc.name).isEqualTo("Header");
        assertThat(mc.fields).hasSize(1);
    }

    @Test
    void protocolConfigShouldHoldTypesAndMessages() {
        ProtocolConfig pc = new ProtocolConfig();

        TypeConfig tc = new TypeConfig();
        tc.name = "u16";
        tc.kind = "uint16";

        MessageConfig mc = new MessageConfig();
        mc.name = "Header";
        mc.fields = List.of();

        pc.types = List.of(tc);
        pc.messages = List.of(mc);

        assertThat(pc.types).hasSize(1);
        assertThat(pc.messages).hasSize(1);
    }
}
