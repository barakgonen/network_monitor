package com.example.schemacore.binaryserdes;

import com.example.schemacore.binaryserdes.config.FieldConfig;
import com.example.schemacore.binaryserdes.config.MessageConfig;
import com.example.schemacore.binaryserdes.config.ProtocolConfig;
import com.example.schemacore.binaryserdes.config.TypeConfig;
import com.example.schemacore.binaryserdes.translators.Double64Translator;
import com.example.schemacore.binaryserdes.translators.DynamicStringTranslator;
import com.example.schemacore.binaryserdes.translators.FixedStringTranslator;
import com.example.schemacore.binaryserdes.translators.Int32Translator;
import com.example.schemacore.binaryserdes.translators.UInt16Translator;
import com.example.schemacore.binaryserdes.translators.UInt32Translator;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Base protocol registry:
 *   - keeps message types by name and by opcode
 *   - provides register & lookup logic
 *   - can initialize itself from a ProtocolConfig (common to In/Out)
 *
 * @param <P> concrete subclass (ProtocolIn, ProtocolOut, etc.)
 */
public abstract class Protocol<P extends Protocol<P>> {

    /** Message types indexed by logical name (e.g. "PositionReport"). */
    protected final Map<String, MessageType> byName = new HashMap<>();

    /** Message types indexed by opcode (wire-level ID). */
    protected final Map<Integer, MessageType> byOpcode = new HashMap<>();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Empty protocol – caller can register messages manually. */
    protected Protocol() {
    }

    /** Protocol initialized from a ProtocolConfig (built-ins + custom types + messages). */
    protected Protocol(ProtocolConfig cfg) {
        initFromConfig(cfg);
    }

    public Map<String, MessageType> getByName() {
        return byName;
    }

    public Map<Integer, MessageType> getByOpcode() {
        return byOpcode;
    }

    /**
     * Populate this protocol from a ProtocolConfig:
     *  - Start with built-in base types
     *  - Add / override with user-defined types from cfg.types
     *  - Build MessageType instances from cfg.messages and register them
     */
    protected void initFromConfig(ProtocolConfig cfg) {
        // 1) start with built-in types (int, double, string, etc.)
        Map<String, Type<?>> typesByName = createBuiltInTypes();

        // 2) user-defined types (may override built-ins if you allow that)
        if (cfg.types != null) {
            for (TypeConfig tc : cfg.types) {
                Type<?> type = buildType(tc, typesByName);
                typesByName.put(tc.name, type);
            }
        }

        // 3) build messages and register them
        if (cfg.messages != null) {
            for (MessageConfig mc : cfg.messages) {
                MessageType.MessageTypeBuilder builder = MessageType.builder()
                        .name(mc.name)
                        .opcode(mc.opcode);

                for (FieldConfig fc : mc.fields) {
                    Type<?> fieldType = typesByName.get(fc.type);
                    if (fieldType == null) {
                        throw new IllegalStateException(
                                "Unknown field type '" + fc.type +
                                        "' in message '" + mc.name + "'"
                        );
                    }

                    @SuppressWarnings({"rawtypes", "unchecked"})
                    MessageField<?> field = new MessageField(fc.name, fieldType);

                    builder.field(field);
                }

                MessageType mt = builder.build();
                registerMessage(mt);
            }
        }
    }

    /**
     * Register a MessageType:
     *  - always by name
     *  - by opcode as well if opcode >= 0
     */
    @SuppressWarnings("unchecked")
    public P registerMessage(MessageType messageType) {
        byName.put(messageType.getName(), messageType);
        if (messageType.getOpcode() >= 0) {
            byOpcode.put(messageType.getOpcode(), messageType);
        }
        return (P) this;
    }

    public MessageType getMessageType(String name) {
        return byName.get(name);
    }

    public MessageType getMessageTypeByOpcode(int opcode) {
        return byOpcode.get(opcode);
    }

    /* =========================================================
       Static helpers – config loading & type construction
       ========================================================= */

    /** Parse a JSON config file into ProtocolConfig. */
    public static ProtocolConfig loadConfig(InputStream in) throws IOException {
        return MAPPER.readValue(in, ProtocolConfig.class);
    }

    /**
     * Built-in types that are always available in configs:
     *
     *  - int32 / int / integer (signed 32-bit)
     *  - uint16 / u16
     *  - uint32 / u32
     *  - double64 / double
     *  - string (length-prefixed UTF-8, variable size)
     */
    private static Map<String, Type<?>> createBuiltInTypes() {
        Map<String, Type<?>> map = new HashMap<>();

        // int32 (signed)
        Type<Integer> int32 = new Type<>(
                "int32",
                4,
                Integer.class,
                new Int32Translator()
        );
        map.put("int32", int32);
        map.put("int", int32);
        map.put("integer", int32);

        // uint16
        Type<Integer> u16 = new Type<>(
                "uint16",
                2,
                Integer.class,
                new UInt16Translator()
        );
        map.put("uint16", u16);
        map.put("u16", u16);

        // uint32
        Type<Long> u32 = new Type<>(
                "uint32",
                4,
                Long.class,
                new UInt32Translator()
        );
        map.put("uint32", u32);
        map.put("u32", u32);

        // double64
        Type<Double> d64 = new Type<>(
                "double64",
                8,
                Double.class,
                new Double64Translator()
        );
        map.put("double64", d64);
        map.put("double", d64);

        // variable-length UTF-8 string, length-prefixed
        Type<String> lpString = new Type<>(
                "string",
                -1,
                String.class,
                new DynamicStringTranslator()
        );
        map.put("string", lpString);
        map.put("lpString", lpString);

        return map;
    }

    /**
     * Build a Type from TypeConfig, given the current type registry (built-ins + previously defined).
     */
    private static Type<?> buildType(TypeConfig tc, Map<String, Type<?>> typesByName) {
        return switch (tc.kind) {
            case "uint16" -> new Type<>(
                    tc.name,
                    2,
                    Integer.class,
                    new UInt16Translator()
            );
            case "uint32" -> new Type<>(
                    tc.name,
                    4,
                    Long.class,
                    new UInt32Translator()
            );
            case "fixedString" -> {
                if (tc.length == null || tc.length <= 0) {
                    throw new IllegalArgumentException(
                            "fixedString type '" + tc.name + "' must have positive 'length'");
                }
                yield new Type<>(
                        tc.name,
                        tc.length,
                        String.class,
                        new FixedStringTranslator(tc.length)
                );
            }
            case "dynamicString" -> new Type<>(
                    tc.name,
                    -1,
                    String.class,
                    new DynamicStringTranslator()
            );
            case "double64" -> new Type<>(
                    tc.name,
                    8,
                    Double.class,
                    new Double64Translator()
            );
            case "int32" -> new Type<>(
                    tc.name,
                    4,
                    Integer.class,
                    new Int32Translator()
            );
            case "record" -> buildRecordType(tc, typesByName);
            default -> throw new IllegalArgumentException("Unknown type kind: " + tc.kind);
        };
    }

    /**
     * Build a RecordType from TypeConfig:
     *  - tc.fields is a list of {name, type} where type refers to an existing type name
     *  - uses LinkedHashMap to preserve field order (binary layout)
     */
    private static RecordType buildRecordType(TypeConfig tc, Map<String, Type<?>> typesByName) {
        if (tc.fields == null || tc.fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "record type '" + tc.name + "' must define non-empty 'fields'");
        }

        Map<String, Type<?>> recordFields = new LinkedHashMap<>();

        for (FieldConfig fc : tc.fields) {
            Type<?> fieldType = typesByName.get(fc.type);
            if (fieldType == null) {
                throw new IllegalStateException(
                        "Unknown field type '" + fc.type + "' in record type '" + tc.name + "'"
                );
            }
            recordFields.put(fc.name, fieldType);
        }

        return new RecordType(tc.name, recordFields);
    }
}
