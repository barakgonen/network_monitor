package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Length-prefixed string:
 * - uint16 length
 * - 'length' bytes of data in given charset
 */
public class DynamicStringTranslator implements Translator<String> {

    private final Charset charset;

    public DynamicStringTranslator() {
        this(StandardCharsets.UTF_8);
    }

    public DynamicStringTranslator(Charset charset) {
        this.charset = charset;
    }

    @Override
    public String fromBytes(ByteBuffer buffer) {
        int length = Short.toUnsignedInt(buffer.getShort());
        if (length > buffer.remaining()) {
            throw new IllegalStateException(
                    "Invalid length prefix " + length + ", remaining: " + buffer.remaining());
        }
        byte[] data = new byte[length];
        buffer.get(data);
        return new String(data, charset);
    }

    @Override
    public void toBytes(String value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        byte[] bytes = value.getBytes(charset);
        if (bytes.length > 0xFFFF) {
            throw new IllegalArgumentException(
                    "String too long for uint16 length prefix: " + bytes.length);
        }
        buffer.putShort((short) bytes.length);
        buffer.put(bytes);
    }
}
