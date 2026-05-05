package com.example.tester.config;

public class PayloadConfig {
    private PayloadMode mode = PayloadMode.TEXT;
    private String text = "";
    private String base64 = "";
    private String hex = "";
    private FruitPayloadConfig fruit = new FruitPayloadConfig();

    public PayloadMode getMode() {
        return mode;
    }

    public void setMode(PayloadMode mode) {
        this.mode = mode;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getBase64() {
        return base64;
    }

    public void setBase64(String base64) {
        this.base64 = base64;
    }

    public String getHex() {
        return hex;
    }

    public void setHex(String hex) {
        this.hex = hex;
    }

    public FruitPayloadConfig getFruit() {
        return fruit;
    }

    public void setFruit(FruitPayloadConfig fruit) {
        this.fruit = fruit;
    }
}
