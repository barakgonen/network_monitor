package com.example.schemacore.reflect;

import com.example.schemacore.MessageDefinition;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * A {@link MessageDefinition} that needs no hand-written pair of definition + codec classes:
 * decode/encode are dispatched reflectively via {@link ReflectiveStructCodec} against the message
 * class's own {@code toByteArray}/{@code fromByteBuffer}-style methods. The message class needs
 * no marker interface - any class following that convention works, including ones owned by an
 * external dependency.
 */
public final class ReflectiveMessageDefinition implements MessageDefinition {
    private final String interfaceName;
    private final String messageType;
    private final int opcode;
    private final Class<?> messageClass;
    private final ByteOrder byteOrder;

    public ReflectiveMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            Class<?> messageClass
    ) {
        this(interfaceName, messageType, opcode, messageClass, ByteOrder.BIG_ENDIAN);
    }

    public ReflectiveMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            Class<?> messageClass,
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
    public Class<?> messageClass() {
        return messageClass;
    }

    @Override
    public Object decodeMessage(ByteBuffer body) throws Exception {
        byte[] bytes = new byte[body.remaining()];
        body.get(bytes);
        return ReflectiveStructCodec.decode(messageClass, bytes, byteOrder);
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) throws Exception {
        return ReflectiveFieldExtractor.extractFields(decodeMessage(body));
    }

    @Override
    public byte[] encodeBody(Object message) throws Exception {
        return ReflectiveStructCodec.encode(message, byteOrder);
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) throws Exception {
        return encodeBody(ReflectiveFieldApplier.build(messageClass, fields));
    }
}
