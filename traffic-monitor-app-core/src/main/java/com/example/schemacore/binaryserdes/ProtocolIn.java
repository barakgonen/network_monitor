package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.config.ProtocolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Inbound protocol:
 * bytes -> JSON
 */
public final class ProtocolIn extends Protocol<ProtocolIn> {

    private ProtocolIn() {
        super();
    }

    private ProtocolIn(ProtocolConfig cfg) {
        super(cfg); // shared init lives in abstract Protocol
    }

    /**
     * Empty protocol – register messages manually.
     */
    public static ProtocolIn create() {
        return new ProtocolIn();
    }

    /**
     * Build ProtocolIn directly from a config InputStream.
     */
    public static ProtocolIn fromConfig(InputStream in) throws IOException {
        ProtocolConfig cfg = Protocol.loadConfig(in);
        return new ProtocolIn(cfg);
    }

    public static ProtocolIn fromProtocolConfig(ProtocolConfig protocolConfig) {
        return new ProtocolIn(protocolConfig);
    }

    /* --------- name-based API --------- */

    public String parse(String messageName, byte[] bytes) {
        MessageType messageType = byName.get(messageName);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown inbound message type: " + messageName);
        }
        return messageType.parseToJson(bytes);
    }

    public String parse(String messageName, ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        return parse(messageName, arr);
    }

    /* --------- opcode-based API --------- */

    public String parse(int opcode, byte[] bytes) {
        MessageType messageType = byOpcode.get(opcode);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown inbound opcode: " + opcode);
        }
        return messageType.parseToJson(bytes);
    }

    public String parse(int opcode, ByteBuffer buffer) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        return parse(opcode, arr);
    }
}
