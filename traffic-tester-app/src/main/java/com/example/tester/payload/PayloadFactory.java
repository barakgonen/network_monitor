package com.example.tester.payload;

import com.example.schemas.fruit.FruitFreshness;
import com.example.schemas.fruit.FruitProtocolCodec;
import com.example.schemas.fruit.OrangeMessage;
import com.example.tester.config.PayloadConfig;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

public class PayloadFactory {
    private final FruitProtocolCodec fruitProtocolCodec = new FruitProtocolCodec();

    public byte[] create(PayloadConfig config) {
        return switch (config.getMode()) {
            case TEXT -> config.getText().getBytes(StandardCharsets.UTF_8);
            case BASE64 -> Base64.getDecoder().decode(config.getBase64());
            case HEX -> parseHex(config.getHex());
            case FRUIT_ORANGE -> createFruitOrange(config);
        };
    }

    private byte[] createFruitOrange(PayloadConfig config) {
        OrangeMessage orangeMessage = new OrangeMessage(
                config.getFruit().getSourceFarm(),
                FruitFreshness.fromWireName(config.getFruit().getFreshness())
        );

        return fruitProtocolCodec.encodeOrange(orangeMessage, Instant.now().toEpochMilli()).payload();
    }

    private byte[] parseHex(String hex) {
        String normalized = hex.replace(" ", "").replace("\n", "").replace("\t", "");

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
