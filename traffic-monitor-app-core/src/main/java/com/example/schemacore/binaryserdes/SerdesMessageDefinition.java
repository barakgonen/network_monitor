package com.example.schemacore.binaryserdes;

import com.example.schemacore.MessageDefinition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

/**
 * A {@link MessageDefinition} backed by the JSON-schema-driven {@link ProtocolIn}/{@link
 * ProtocolOut} engine instead of a reflectively-codable {@code Class<?>} (compare with {@link
 * com.example.schemacore.reflect.ReflectiveMessageDefinition}). All messages declared in one
 * {@code serdesFile:} share the same {@link ProtocolIn}/{@link ProtocolOut} pair, built once from
 * that file's {@link com.example.schemacore.binaryserdes.config.ProtocolConfig}; each message gets
 * its own {@code SerdesMessageDefinition} carrying just its own name/opcode.
 *
 * <p>There is no backing Java class, so {@link #messageClass()} returns {@code null} - safe
 * because {@code MessageDefinitionRegistry} excludes {@code null} from its class-uniqueness check.
 */
public final class SerdesMessageDefinition implements MessageDefinition {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> FIELD_MAP_TYPE = new TypeReference<>() {
    };

    private final String interfaceName;
    private final String messageType;
    private final int opcode;
    private final ProtocolIn protocolIn;
    private final ProtocolOut protocolOut;
    private final ByteOrder byteOrder;

    public SerdesMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            ProtocolIn protocolIn,
            ProtocolOut protocolOut
    ) {
        this(interfaceName, messageType, opcode, protocolIn, protocolOut, ByteOrder.BIG_ENDIAN);
    }

    /** {@code byteOrder} matters for interfaces like rada-le that decode the same message shape as a different-endian interface. */
    public SerdesMessageDefinition(
            String interfaceName,
            String messageType,
            int opcode,
            ProtocolIn protocolIn,
            ProtocolOut protocolOut,
            ByteOrder byteOrder
    ) {
        this.interfaceName = interfaceName;
        this.messageType = messageType;
        this.opcode = opcode;
        this.protocolIn = protocolIn;
        this.protocolOut = protocolOut;
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
        return null;
    }

    @Override
    public Map<String, Object> decodeBody(ByteBuffer body) throws Exception {
        byte[] bytes = new byte[body.remaining()];
        body.get(bytes);
        String json = protocolIn.parse(opcode, bytes, byteOrder);
        return MAPPER.readValue(json, FIELD_MAP_TYPE);
    }

    @Override
    public Object decodeMessage(ByteBuffer body) throws Exception {
        return decodeBody(body);
    }

    @Override
    public byte[] encodeBody(Map<String, Object> fields) throws Exception {
        return encodeJson(MAPPER.writeValueAsString(fields));
    }

    @Override
    public byte[] encodeBody(Object message) throws Exception {
        if (message instanceof Map<?, ?> fields) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typedFields = (Map<String, Object>) fields;
            return encodeBody(typedFields);
        }

        return encodeJson(MAPPER.writeValueAsString(message));
    }

    /**
     * {@link ProtocolOut#encode(int, String)} pre-sizes its buffer by summing every field's fixed
     * {@code sizeInBytes} and throws if any field is variable-length (e.g. a {@code string}) - it
     * has no way to know the encoded size of a variable field before writing it. Every legacy
     * envelope protocol this class backs (fruit/weather/candy/greeting) has at least one string
     * field, so encoding here instead over-allocates a buffer generously (the encoded field values
     * can never exceed their own JSON representation's UTF-8 byte count by more than a small,
     * bounded per-field overhead - length prefixes, and JSON's own quoting/escaping already costs
     * more bytes than the raw framing it replaces) and trims to the bytes {@link
     * ProtocolOut#encodeInto} actually wrote.
     */
    private byte[] encodeJson(String json) throws Exception {
        int bufferSize = json.getBytes(StandardCharsets.UTF_8).length + 1024;
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize).order(byteOrder);
        protocolOut.encodeInto(opcode, json, buffer);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }
}
