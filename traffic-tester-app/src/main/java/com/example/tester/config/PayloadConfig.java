package com.example.tester.config;

public class PayloadConfig {
    private PayloadMode mode = PayloadMode.TEXT;
    private String text = "";
    private String base64 = "";
    private String hex = "";

    /**
     * Optional per-message target override.
     * If not provided, the scenario-level UDP target is used.
     */
    private PayloadTargetConfig target = new PayloadTargetConfig();

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

    public PayloadTargetConfig getTarget() {
        return target;
    }

    public void setTarget(PayloadTargetConfig target) {
        this.target = target;
    }
}
