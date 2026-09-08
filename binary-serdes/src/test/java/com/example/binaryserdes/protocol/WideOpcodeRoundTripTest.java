package com.example.binaryserdes.protocol;

import com.example.binaryserdes.MessageType;
import com.example.binaryserdes.Protocol;
import com.example.binaryserdes.ProtocolIn;
import com.example.binaryserdes.ProtocolOut;
import com.example.binaryserdes.config.ProtocolConfig;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves opcode values outside the 32-bit range - including the unsigned-only upper half of the
 * 64-bit range (opcode literals written as a quoted string in protocol JSON, parsed via
 * {@link com.example.binaryserdes.config.UnsignedLongDeserializer}) - round-trip correctly as raw
 * 64-bit bit patterns through {@link MessageType}/{@link Protocol}/{@link ProtocolIn}/
 * {@link ProtocolOut}.
 */
class WideOpcodeRoundTripTest {

    private ProtocolConfig loadConfig() throws Exception {
        InputStream in = getClass().getResourceAsStream("/binaryserdes/protocol/wide-opcode-protocol.json");
        assertThat(in).withFailMessage("Config resource not found").isNotNull();
        return Protocol.loadConfig(in);
    }

    @Test
    void allOnesUnsignedOpcodeLiteral_parsesAsMinusOne() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MessageType messageType = protocolIn.getMessageType("MaxUnsignedOpcodeMessage");

        assertThat(messageType.hasOpcode()).isTrue();
        assertThat(messageType.getOpcode()).isEqualTo(-1L);
        assertThat(Long.toUnsignedString(messageType.getOpcode())).isEqualTo("18446744073709551615");
    }

    @Test
    void signBitUnsignedOpcodeLiteral_parsesAsLongMinValue() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MessageType messageType = protocolIn.getMessageType("SignBitOpcodeMessage");

        assertThat(messageType.getOpcode()).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void plainNumericOpcodeWithinSignedRange_parsesUnchanged() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);

        MessageType messageType = protocolIn.getMessageType("PlainLongOpcodeMessage");

        assertThat(messageType.getOpcode()).isEqualTo(4611686018427387903L);
    }

    @Test
    void protocolAndProtocolInOut_resolveWideOpcodesByBitPattern() throws Exception {
        ProtocolConfig cfg = loadConfig();
        ProtocolIn protocolIn = ProtocolIn.fromProtocolConfig(cfg);
        ProtocolOut protocolOut = ProtocolOut.fromProtocolConfig(cfg);

        assertThat(protocolIn.getMessageTypeByOpcode(-1L).getName()).isEqualTo("MaxUnsignedOpcodeMessage");
        assertThat(protocolIn.getMessageTypeByOpcode(Long.MIN_VALUE).getName()).isEqualTo("SignBitOpcodeMessage");

        byte[] bytes = protocolOut.encode(-1L, "{\"value\": 42}");
        String json = protocolIn.parse(-1L, bytes);

        assertThat(json).contains("42");
    }
}
