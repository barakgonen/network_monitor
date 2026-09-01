package com.example.schemacore;

import java.nio.ByteOrder;
import java.util.Map;

/**
 * Decodes a fixed-size header at the start of a payload into a generic field map, so
 * {@code MessageIngestionPipeline} can read the opcode field (and, for the legacy envelope path,
 * the body-length field) before it knows which {@link MessageDefinition} applies. Two
 * implementations: {@code com.example.schemacore.reflect.ReflectiveHeaderDecoder} (a Java
 * header class, e.g. {@code DefaultEnvelopeHeader}) and {@code
 * com.example.monitor.schema.SerdesHeaderDecoder} (a {@code record} type declared in a
 * {@code serdesFile:}, for {@code messageOwnsHeader} interfaces whose header has no backing Java
 * class, e.g. rada).
 */
public interface HeaderDecoder {

    /** Fixed size of the header in bytes. */
    int headerSize();

    /** Decodes exactly {@link #headerSize()} bytes into a field-name -&gt; value map. */
    Map<String, Object> decode(byte[] headerBytes, ByteOrder byteOrder) throws Exception;
}
