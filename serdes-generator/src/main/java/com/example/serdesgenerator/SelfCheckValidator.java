package com.example.serdesgenerator;

import com.example.binaryserdes.config.FieldConfig;
import com.example.binaryserdes.config.MessageConfig;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.binaryserdes.config.TypeConfig;
import com.example.schemacore.reflect.StructSizeCalculator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Independently cross-checks a freshly generated {@link ProtocolConfig} against {@link
 * StructSizeCalculator}'s own field-reflection-based sizing for each root message class - two
 * independently-derived sizes that must agree if the generator's type-mapping table is correct.
 * Not a hard gate ({@link GeneratorMain} only warns on mismatch): its purpose is to catch a
 * mapping-table bug before it becomes a silent wire mismatch at runtime.
 */
public final class SelfCheckValidator {

    private static final Map<String, Integer> BUILTIN_SIZES = builtinSizes();

    public record Mismatch(String messageName, int expectedSize, int actualSize) {
    }

    public List<Mismatch> validate(ProtocolConfig config, List<ProtocolJsonGenerator.RootMessage> roots) {
        Map<String, TypeConfig> typesByName = new HashMap<>();
        if (config.types != null) {
            for (TypeConfig tc : config.types) {
                typesByName.put(tc.name, tc);
            }
        }

        Map<String, MessageConfig> messagesByName = new HashMap<>();
        if (config.messages != null) {
            for (MessageConfig mc : config.messages) {
                messagesByName.put(mc.name, mc);
            }
        }

        List<Mismatch> mismatches = new ArrayList<>();
        for (ProtocolJsonGenerator.RootMessage root : roots) {
            String name = root.messageClass().getSimpleName();
            MessageConfig mc = messagesByName.get(name);
            if (mc == null) {
                throw new IllegalStateException("Generated config missing message: " + name);
            }

            int expected = StructSizeCalculator.calculateStructSize(root.messageClass());
            int actual = messageSize(mc, typesByName);
            if (expected != actual) {
                mismatches.add(new Mismatch(name, expected, actual));
            }
        }
        return mismatches;
    }

    private int messageSize(MessageConfig mc, Map<String, TypeConfig> typesByName) {
        int total = 0;
        for (FieldConfig fc : mc.fields) {
            total += typeSize(fc.type, typesByName);
        }
        return total;
    }

    private int typeSize(String typeName, Map<String, TypeConfig> typesByName) {
        Integer builtin = BUILTIN_SIZES.get(typeName);
        if (builtin != null) {
            return builtin;
        }

        TypeConfig tc = typesByName.get(typeName);
        if (tc == null) {
            throw new IllegalStateException("Unknown type referenced in generated config: " + typeName);
        }

        return switch (tc.kind) {
            case "record" -> messageSize(recordAsMessage(tc), typesByName);
            case "array" -> tc.length * typeSize(tc.elementType, typesByName);
            case "enum" -> typeSize(tc.underlyingType, typesByName);
            default -> throw new IllegalStateException("Unexpected generated type kind: " + tc.kind);
        };
    }

    private MessageConfig recordAsMessage(TypeConfig tc) {
        MessageConfig mc = new MessageConfig();
        mc.fields = tc.fields;
        return mc;
    }

    private static Map<String, Integer> builtinSizes() {
        Map<String, Integer> map = new HashMap<>();
        map.put("int32", 4);
        map.put("int", 4);
        map.put("integer", 4);
        map.put("uint16", 2);
        map.put("u16", 2);
        map.put("uint32", 4);
        map.put("u32", 4);
        map.put("double64", 8);
        map.put("double", 8);
        map.put("uint8", 1);
        map.put("u8", 1);
        map.put("byte", 1);
        map.put("float32", 4);
        map.put("float", 4);
        map.put("int64", 8);
        map.put("uint64", 8);
        map.put("long", 8);
        return map;
    }
}
