package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.config.ProtocolConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Outbound protocol:
 *    JSON -> bytes
 */
public final class ProtocolOut extends Protocol<ProtocolOut> {

    private ProtocolOut() {
        super();
    }

    private ProtocolOut(ProtocolConfig cfg) {
        super(cfg);
    }

    /** Empty protocol – register messages manually. */
    public static ProtocolOut create() {
        return new ProtocolOut();
    }

    /** Build ProtocolOut directly from a config InputStream. */
    public static ProtocolOut fromConfig(InputStream in) throws IOException {
        ProtocolConfig cfg = Protocol.loadConfig(in);
        return new ProtocolOut(cfg);
    }

    public static ProtocolOut fromProtocolConfig(ProtocolConfig protocolConfig) {
        return new ProtocolOut(protocolConfig);
    }

    /* --------- name-based API --------- */

    public byte[] encode(String messageName, String json) throws IOException {
        return encode(messageName, json, ByteOrder.BIG_ENDIAN);
    }

    public byte[] encode(String messageName, String json, ByteOrder byteOrder) throws IOException {
        MessageType messageType = byName.get(messageName);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown outbound message type: " + messageName);
        }
        return messageType.toBytes(json, byteOrder);
    }

    public void encodeInto(String messageName, String json, ByteBuffer buffer) throws IOException {
        MessageType messageType = byName.get(messageName);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown outbound message type: " + messageName);
        }
        messageType.writeToBuffer(json, buffer);
    }

    /* --------- opcode-based API --------- */

    public byte[] encode(int opcode, String json) throws IOException {
        return encode(opcode, json, ByteOrder.BIG_ENDIAN);
    }

    public byte[] encode(int opcode, String json, ByteOrder byteOrder) throws IOException {
        MessageType messageType = byOpcode.get(opcode);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown outbound opcode: " + opcode);
        }
        return messageType.toBytes(json, byteOrder);
    }

    public void encodeInto(int opcode, String json, ByteBuffer buffer) throws IOException {
        MessageType messageType = byOpcode.get(opcode);
        if (messageType == null) {
            throw new IllegalArgumentException("Unknown outbound opcode: " + opcode);
        }
        messageType.writeToBuffer(json, buffer);
    }
}
