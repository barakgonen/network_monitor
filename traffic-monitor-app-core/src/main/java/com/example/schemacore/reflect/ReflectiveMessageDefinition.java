package com.example.schemacore.reflect;

import com.example.schemacore.MessageDefinition;
import com.example.schemacore.ProtocolMessage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * A {@link MessageDefinition} that needs no hand-written pair of definition + codec classes:
 * decode/encode are dispatched reflectively via {@link ReflectiveStructCodec} against the message
 * class's own {@code toByteArray}/{@code fromByteBuffer}-style methods.
 */
public final class ReflectiveMessageDefinition implements MessageDefinition {
    private final String interfaceName;
    private final String messageType;
    private final int opcode;
    private final Class<? extends ProtocolMessage> messageClass;
    private final ByteOrder byteOrder;

    public ReflectiveMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            Class<? extends ProtocolMessage> messageClass
    ) {
        this(interfaceName, messageType, opcode, messageClass, ByteOrder.BIG_ENDIAN);
    }

    public ReflectiveMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            Class<? extends ProtocolMessage> messageClass,
            ByteOrder byteOrder
    ) {
        this.interfaceName = interfaceName;
        this.messageType = messageType;
        this.opcode = opcode;
        this.messageClass = messageClass;
        this.byteOrder = byteOrder;
    }

    @Override
    public String interfaceName() {
        return interfaceName;
    }

    @Override
    public String messageType() {
        return messageType;
    }

    @Override
    public int opcode() {
        return opcode;
    }

    @Override
    public Class<? extends ProtocolMessage> messageClass() {
        return messageClass;
    }

    @Override
    public ProtocolMessage decodeMessage(ByteBuffer body) throws Exception {
        byte[] bytes = new byte[body.remaining()];
        body.get(bytes);
        return messageClass.cast(ReflectiveStructCodec.decode(messageClass, bytes, byteOrder));
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) throws Exception {
        return ReflectiveFieldExtractor.extractFields(decodeMessage(body));
    }

    @Override
    public byte[] encodeBody(ProtocolMessage message) throws Exception {
        return ReflectiveStructCodec.encode(message, byteOrder);
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) throws Exception {
        return encodeBody(ReflectiveFieldApplier.build(messageClass, fields));
    }
}
