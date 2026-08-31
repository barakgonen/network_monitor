package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.config.ProtocolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
        return parse(messageName, bytes, ByteOrder.BIG_ENDIAN);
    }

    public String parse(String messageName, byte[] bytes, ByteOrder byteOrder) {
        MessageType messageType = byName.get(messageName);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown inbound message type: " + messageName);
        }
        return messageType.parseToJson(bytes, byteOrder);
    }

    public String parse(String messageName, ByteBuffer buffer) {
        return parse(messageName, buffer, ByteOrder.BIG_ENDIAN);
    }

    public String parse(String messageName, ByteBuffer buffer, ByteOrder byteOrder) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        return parse(messageName, arr, byteOrder);
    }

    /* --------- opcode-based API --------- */

    public String parse(int opcode, byte[] bytes) {
        return parse(opcode, bytes, ByteOrder.BIG_ENDIAN);
    }

    public String parse(int opcode, byte[] bytes, ByteOrder byteOrder) {
        MessageType messageType = byOpcode.get(opcode);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown inbound opcode: " + opcode);
        }
        return messageType.parseToJson(bytes, byteOrder);
    }

    public String parse(int opcode, ByteBuffer buffer) {
        return parse(opcode, buffer, ByteOrder.BIG_ENDIAN);
    }

    public String parse(int opcode, ByteBuffer buffer, ByteOrder byteOrder) {
        byte[] arr = new byte[buffer.remaining()];
        buffer.get(arr);
        return parse(opcode, arr, byteOrder);
    }
}
