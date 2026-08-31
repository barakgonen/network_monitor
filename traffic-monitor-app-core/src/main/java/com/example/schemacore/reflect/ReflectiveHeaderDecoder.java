package com.example.schemacore.reflect;

import com.example.schemacore.HeaderDecoder;

import java.nio.ByteOrder;
import java.util.Map;

/**
 * The original header-decode behavior {@code MessageIngestionPipeline} always used before
 * {@link com.example.schemacore.binaryserdes.SerdesHeaderDecoder} existed: a Java header class
 * (e.g. {@code DefaultEnvelopeHeader}, or rada's own header class back when it lived in this
 * module) decoded via {@link ReflectiveStructCodec}/{@link ReflectiveFieldExtractor}.
 */
public final class ReflectiveHeaderDecoder implements HeaderDecoder {

    private final Class<?> headerType;
    private final int headerSize;

    public ReflectiveHeaderDecoder(Class<?> headerType) {
        this.headerType = headerType;
        this.headerSize = StructSizeCalculator.calculateStructSize(headerType);
    }

    @Override
    public int headerSize() {
        return headerSize;
    }

    @Override
    public Map<String, Object> decode(byte[] headerBytes, ByteOrder byteOrder) throws Exception {
        Object header = ReflectiveStructCodec.decode(headerType, headerBytes, byteOrder);
        return ReflectiveFieldExtractor.extractFields(header);
    }
}
