package com.example.binaryserdes.translators;

import com.example.binaryserdes.Translator;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Fixed-length ASCII/charset string.
 * Reads/writes exactly 'length' bytes, trims trailing 0x00 on read,
 * pads with 0x00 on write.
 */
public class FixedStringTranslator implements Translator<String> {

    private final int length;
    private final Charset charset;

    public FixedStringTranslator(int length) {
        this(length, StandardCharsets.US_ASCII);
    }

    public FixedStringTranslator(int length, Charset charset) {
        if (length <= 0) {
            throw new IllegalArgumentException("length must be > 0");
        }
        this.length = length;
        this.charset = charset;
    }

    @Override
    public String fromBytes(ByteBuffer buffer) {
        byte[] dst = new byte[length];
        buffer.get(dst);

        int end = 0;
        while (end < dst.length && dst[end] != 0x00) {
            end++;
        }
        return new String(dst, 0, end, charset);
    }

    @Override
    public void toBytes(String value, ByteBuffer buffer) {
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        byte[] src = value.getBytes(charset);
        if (src.length > length) {
            throw new IllegalArgumentException(
                    "String too long for fixed length " + length + ": " + src.length);
        }

        buffer.put(src);
        // pad with zeros
        for (int i = src.length; i < length; i++) {
            buffer.put((byte) 0x00);
        }
    }
}
