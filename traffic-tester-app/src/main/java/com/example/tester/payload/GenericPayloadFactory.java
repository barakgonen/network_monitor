package com.example.tester.payload;

import com.example.tester.config.PayloadConfig;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GenericPayloadFactory {
    public byte[] create(PayloadConfig config) {
        return switch (config.getMode()) {
            case TEXT -> config.getText().getBytes(StandardCharsets.UTF_8);
            case BASE64 -> Base64.getDecoder().decode(config.getBase64());
            case HEX -> parseHex(config.getHex());
        };
    }

    private byte[] parseHex(String hex) {
        String normalized = hex
                .replace(" ", "")
                .replace("\n", "")
                .replace("\t", "");

        if (normalized.length() % 2 != 0) {
            throw new IllegalArgumentException("HEX payload must contain an even number of characters");
        }

        byte[] bytes = new byte[normalized.length() / 2];

        for (int i = 0; i < normalized.length(); i += 2) {
            String pair = normalized.substring(i, i + 2);
            bytes[i / 2] = (byte) Integer.parseInt(pair, 16);
        }

        return bytes;
    }
}
