package com.example.binaryserdes;

/**
 * Describes a logical type in the protocol.
 *
 * - name: logical type name
 * - sizeInBytes: fixed size in bytes, or -1 for variable length
 * - javaType: (optional) runtime class of T, used for JSON -> bytes
 * - translator: bytes <-> T logic
 */
public class Type<T> {

    private final String name;
    private final int sizeInBytes;
    private final Class<T> javaType; // may be null
    private final Translator<T> translator;

    /**
     * Simple constructor: no javaType provided.
     * Good enough for bytes -> JSON extraction.
     */
    public Type(String name, int sizeInBytes, Translator<T> translator) {
        this(name, sizeInBytes, null, translator);
    }

    /**
     * Full constructor: with javaType.
     * Enables automatic JSON -> bytes conversion.
     */
    public Type(String name, int sizeInBytes, Class<T> javaType, Translator<T> translator) {
        if (javaType != null && javaType.isInterface()) {
            throw new IllegalArgumentException("javaType must not be an interface: " + javaType);
        }
        if (javaType != null && java.lang.reflect.Modifier.isAbstract(javaType.getModifiers())) {
            throw new IllegalArgumentException("javaType must be concrete: " + javaType);
        }

        this.name = name;
        this.sizeInBytes = sizeInBytes;
        this.javaType = javaType;
        this.translator = translator;
    }

    public String getName() {
        return name;
    }

    public int getSizeInBytes() {
        return sizeInBytes;
    }

    public Class<T> getJavaType() {
        return javaType;
    }

    public Translator<T> getTranslator() {
        return translator;
    }
}
