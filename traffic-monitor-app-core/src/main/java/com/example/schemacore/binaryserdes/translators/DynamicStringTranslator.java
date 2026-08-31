package com.example.schemacore.binaryserdes.translators;

import com.example.schemacore.binaryserdes.Translator;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;


/**
 * Length-prefixed string:
 * - int32 length
 * - 'length' bytes of data in given charset
 *
 * <p>The prefix is 4 bytes (not 2) to match the wire format every legacy-envelope message class
 * this engine replaces already used (e.g. {@code OrangeMessage}/{@code CandyMessage}'s
 * hand-written {@code buffer.putInt(bytes.length)}) - traffic-tester-app's message classes and
 * {@code TestProtocolPayloads} both still encode with a 4-byte prefix, so this has to match or
 * every {@code string}-field message decodes with a garbled/empty value once real UDP/TCP traffic
 * lands.
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
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
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
        buffer.putInt(bytes.length);
        buffer.put(bytes);
    }
}
