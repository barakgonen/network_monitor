package com.example.serdesgenerator;

import com.example.binaryserdes.config.FieldConfig;
import com.example.binaryserdes.config.MessageConfig;
import com.example.binaryserdes.config.ProtocolConfig;
import com.example.binaryserdes.config.TypeConfig;
import com.example.schemacore.annotation.EnumWireSize;
import com.example.schemacore.reflect.ReflectiveStructCodec;
import com.example.schemacore.reflect.StructSizeCalculator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reflects over "fixed-layout" message classes (the ones {@link StructSizeCalculator} can size -
 * no {@code String} fields, everything else a fixed scalar, {@code @FixedArrayLength} array,
 * enum, or nested struct) and produces the equivalent {@link ProtocolConfig} tree that
 * {@code binary-serdes}' {@code Protocol.loadConfig}/{@code ProtocolIn}/{@code ProtocolOut} would
 * otherwise require hand-authoring as a {@code *.protocol.json} file.
 *
 * Deliberately out of scope: classes with {@code String} fields (their wire layout is hand-coded,
 * not mechanically derivable - see the module's package-info for why) and {@code boolean} fields
 * (no built-in wire type covers them in {@code binary-serdes} today).
 */
public final class ProtocolJsonGenerator {

    private static final int DEFAULT_ENUM_WIRE_SIZE_BYTES = Integer.BYTES;

    public record RootMessage(Class<?> messageClass, int opcode) {
        public RootMessage {
            if (messageClass == null) {
                throw new IllegalArgumentException("messageClass is required");
            }
        }
    }

    /**
     * Generates one {@link ProtocolConfig} covering every root message given, sharing a single
     * {@code types} registry across all of them so a type referenced by more than one message
     * (e.g. a common header struct) is only emitted once.
     */
    public ProtocolConfig generate(List<RootMessage> roots) {
        if (roots == null || roots.isEmpty()) {
            throw new IllegalArgumentException("At least one root message is required");
        }

        TypeRegistry registry = new TypeRegistry();
        List<MessageConfig> messages = new ArrayList<>();

        for (RootMessage root : roots) {
            messages.add(buildMessage(root, registry));
        }

        ProtocolConfig cfg = new ProtocolConfig();
        cfg.types = registry.values();
        cfg.messages = messages;
        return cfg;
    }

    private MessageConfig buildMessage(RootMessage root, TypeRegistry registry) {
        Class<?> messageClass = root.messageClass();

        // Fail fast if the class doesn't even follow the reflective codec convention - same
        // check MessageSchemaWiringConfig.resolveDefinition runs for messageClass: entries.
        ReflectiveStructCodec.requireEncodable(messageClass);
        ReflectiveStructCodec.requireDecodable(messageClass);

        MessageConfig mc = new MessageConfig();
        mc.name = messageClass.getSimpleName();
        mc.opcode = root.opcode();
        mc.fields = buildFields(messageClass, registry, new HashSet<>());
        return mc;
    }

    private List<FieldConfig> buildFields(Class<?> type, TypeRegistry registry, Set<Class<?>> visiting) {
        if (!visiting.add(type)) {
            throw new IllegalStateException("Recursive struct detected: " + type.getName());
        }

        try {
            List<FieldConfig> fields = new ArrayList<>();

            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                FieldConfig fc = new FieldConfig();
                fc.name = field.getName();
                fc.type = resolveFieldType(field, registry, visiting);
                fields.add(fc);
            }

            if (fields.isEmpty()) {
                throw new IllegalArgumentException("No serializable fields found on " + type.getName());
            }

            return fields;
        } finally {
            visiting.remove(type);
        }
    }

    private String resolveFieldType(Field field, TypeRegistry registry, Set<Class<?>> visiting) {
        Class<?> fieldType = field.getType();

        if (fieldType == String.class || CharSequence.class.isAssignableFrom(fieldType)) {
            throw new IllegalArgumentException(
                    "String field not supported - this generator only handles fixed-layout classes "
                            + "(no variable-length fields): " + describeField(field));
        }

        if (fieldType.isArray()) {
            return resolveArrayType(field, registry, visiting);
        }

        if (fieldType.isEnum()) {
            return resolveEnumType(fieldType, field, registry);
        }

        if (StructSizeCalculator.isFixedScalar(fieldType)) {
            return scalarTypeName(fieldType, field);
        }

        return resolveRecordType(fieldType, registry, visiting);
    }

    /**
     * Maps a Java fixed-scalar type to its wire type name. {@code int}/{@code Integer} always
     * maps to {@code int32}, never {@code uint32} - both are registered as 4-byte types in
     * {@code binary-serdes} and either could in principle back a Java {@code int} field, so this
     * choice is genuinely not derivable from the field's declared type alone. Confirmed by
     * example: in the hand-authored {@code rada.protocol.json}, {@code RadaStatus}'s {@code
     * radarSoftwareVersion} (an {@code int} field with an {@code Integer.toUnsignedLong(...)}
     * getter) is declared {@code uint32}, while {@code RadaTrackData}'s {@code id} - the exact
     * same shape, same unsigned-getter pattern - is declared {@code int32}. Since even that
     * "unsigned getter present" signal doesn't reliably predict the hand-authored choice, this
     * generator doesn't attempt to guess it either: {@code int32} is always used, and a field that
     * needs {@code uint32} semantics (e.g. so large values decode as a positive JSON number
     * instead of a negative one) needs its generated type name hand-corrected afterward. The wire
     * bytes are identical either way - only the JSON-level signedness interpretation differs.
     */
    private String scalarTypeName(Class<?> fieldType, Field field) {
        if (fieldType == byte.class || fieldType == Byte.class) {
            return "uint8";
        }
        if (fieldType == short.class || fieldType == Short.class
                || fieldType == char.class || fieldType == Character.class) {
            return "uint16";
        }
        if (fieldType == int.class || fieldType == Integer.class) {
            return "int32";
        }
        if (fieldType == long.class || fieldType == Long.class) {
            return "int64";
        }
        if (fieldType == float.class || fieldType == Float.class) {
            return "float32";
        }
        if (fieldType == double.class || fieldType == Double.class) {
            return "double64";
        }
        if (fieldType == boolean.class || fieldType == Boolean.class) {
            throw new IllegalArgumentException(
                    "boolean field not supported - binary-serdes has no built-in bool wire type: "
                            + describeField(field));
        }
        throw new IllegalArgumentException("Unsupported scalar type: " + fieldType.getName());
    }

    private String resolveRecordType(Class<?> type, TypeRegistry registry, Set<Class<?>> visiting) {
        String name = type.getSimpleName();
        if (registry.contains(name)) {
            registry.requireSameSource(name, type);
            return name;
        }

        // Recurse (and thus register any nested types) before registering this record itself, so
        // referenced types are always declared earlier in the output - Protocol.buildTypesByName
        // requires declaration order for forward references.
        List<FieldConfig> fields = buildFields(type, registry, visiting);

        TypeConfig tc = new TypeConfig();
        tc.name = name;
        tc.kind = "record";
        tc.fields = fields;
        registry.put(name, tc, type);
        return name;
    }

    private String resolveArrayType(Field field, TypeRegistry registry, Set<Class<?>> visiting) {
        int length = StructSizeCalculator.resolveArrayLength(field);
        if (length <= 0) {
            throw new IllegalArgumentException("@FixedArrayLength must be positive: " + describeField(field));
        }

        Class<?> componentType = field.getType().getComponentType();
        if (componentType.isArray()) {
            throw new IllegalArgumentException("Nested arrays are not supported: " + describeField(field));
        }
        if (componentType == String.class || CharSequence.class.isAssignableFrom(componentType)) {
            throw new IllegalArgumentException("String array component not supported: " + describeField(field));
        }

        String elementTypeName;
        if (componentType.isEnum()) {
            elementTypeName = resolveEnumType(componentType, field, registry);
        } else if (StructSizeCalculator.isFixedScalar(componentType)) {
            elementTypeName = scalarTypeName(componentType, field);
        } else {
            elementTypeName = resolveRecordType(componentType, registry, visiting);
        }

        // Deterministic from (elementTypeName, length) alone, so a name collision here always
        // means an identical shape - no source-class tracking needed, unlike record/enum names
        // (which come from a bare Java simple name and so can legitimately collide across two
        // unrelated classes).
        String arrayTypeName = elementTypeName + "Array" + length;
        if (registry.contains(arrayTypeName)) {
            return arrayTypeName;
        }

        TypeConfig tc = new TypeConfig();
        tc.name = arrayTypeName;
        tc.kind = "array";
        tc.elementType = elementTypeName;
        tc.length = length;
        registry.put(arrayTypeName, tc, null);
        return arrayTypeName;
    }

    private String resolveEnumType(Class<?> enumType, Field field, TypeRegistry registry) {
        String name = enumType.getSimpleName();
        if (registry.contains(name)) {
            registry.requireSameSource(name, enumType);
            return name;
        }

        int wireSizeBytes = enumWireSizeBytes(field);
        String underlyingType = switch (wireSizeBytes) {
            case 1 -> "uint8";
            case 2 -> "uint16";
            case 4 -> "int32";
            case 8 -> "int64";
            default -> throw new IllegalStateException("Unreachable enum wire size: " + wireSizeBytes);
        };

        Method codeMethod = findCodeMethod(enumType);
        Map<String, Integer> values = new LinkedHashMap<>();
        for (Object constant : enumType.getEnumConstants()) {
            Enum<?> enumConstant = (Enum<?>) constant;
            int code = codeMethod != null ? invokeCode(codeMethod, constant) : enumConstant.ordinal();
            values.put(enumConstant.name(), code);
        }

        TypeConfig tc = new TypeConfig();
        tc.name = name;
        tc.kind = "enum";
        tc.underlyingType = underlyingType;
        tc.values = values;
        registry.put(name, tc, enumType);
        return name;
    }

    /**
     * Mirrors {@code StructSizeCalculator.enumWireSize}: {@code @EnumWireSize} on the field
     * overrides, default is 4 bytes.
     */
    private int enumWireSizeBytes(Field field) {
        EnumWireSize annotation = field.getAnnotation(EnumWireSize.class);
        if (annotation == null) {
            return DEFAULT_ENUM_WIRE_SIZE_BYTES;
        }

        int value = annotation.value();
        if (value == Byte.BYTES || value == Short.BYTES || value == Integer.BYTES || value == Long.BYTES) {
            return value;
        }

        throw new IllegalArgumentException(
                "@EnumWireSize must be one of 1, 2, 4, 8 bytes: " + describeField(field) + ", actual=" + value);
    }

    /**
     * Prefers a numeric {@code getCode()} accessor (as {@code TemperatureUnit}/{@code
     * FruitFreshness} declare) for the wire code per constant; falls back to {@link Enum#ordinal()}
     * when absent - confirmed necessary by {@code FavoriteColor} (used by
     * {@code RadaExtendedStatusMrs}), which has no {@code getCode()} at all and is decoded via
     * {@code values()[byteBuffer.getInt()]}, i.e. plain ordinal.
     */
    private Method findCodeMethod(Class<?> enumType) {
        try {
            Method method = enumType.getMethod("getCode");
            Class<?> returnType = method.getReturnType();
            if (returnType == byte.class || returnType == short.class
                    || returnType == int.class || returnType == long.class) {
                return method;
            }
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private int invokeCode(Method method, Object instance) {
        try {
            return ((Number) method.invoke(instance)).intValue();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to invoke getCode() on " + instance, e);
        }
    }

    private String describeField(Field field) {
        return field.getDeclaringClass().getName() + "." + field.getName();
    }

    /**
     * The shared {@code types:} registry threaded through a whole {@link #generate} run. Tracks,
     * alongside each {@link TypeConfig}, which Java class produced a record/enum entry (arrays
     * don't need this - their name is fully determined by element type + length, so a name match
     * always means an identical shape) so two unrelated classes that happen to share a simple
     * name (e.g. two different {@code Header} classes in different packages) fail loudly instead
     * of silently colliding into one wrong type.
     */
    private static final class TypeRegistry {
        private final Map<String, TypeConfig> typesByName = new LinkedHashMap<>();
        private final Map<String, Class<?>> sourceByName = new LinkedHashMap<>();

        boolean contains(String name) {
            return typesByName.containsKey(name);
        }

        void put(String name, TypeConfig tc, Class<?> source) {
            typesByName.put(name, tc);
            if (source != null) {
                sourceByName.put(name, source);
            }
        }

        void requireSameSource(String name, Class<?> type) {
            Class<?> existingSource = sourceByName.get(name);
            if (existingSource != null && !existingSource.equals(type)) {
                throw new IllegalStateException(
                        "Type name collision for '" + name + "': already generated from "
                                + existingSource.getName() + ", now also requested from " + type.getName());
            }
        }

        List<TypeConfig> values() {
            return new ArrayList<>(typesByName.values());
        }
    }
}
