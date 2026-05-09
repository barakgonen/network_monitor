package com.example.tester.config;

import java.util.List;

public class TesterScenario {
    private UdpConfig udp;
    private UdpListenerConfig listener = new UdpListenerConfig();
    private ReaderConfig reader = new ReaderConfig();

    /**
     * Preferred V2 format: send multiple messages in one tester run.
     */
    private List<PayloadConfig> messages;

    /**
     * Backward-compatible V1 format: single payload.
     */
    private PayloadConfig payload;

    private int repeat = 1;
    private long intervalMillis = 1000;

    public UdpConfig getUdp() {
        return udp;
    }

    public void setUdp(UdpConfig udp) {
        this.udp = udp;
    }

    public ReaderConfig getReader() {
        return reader;
    }

    public void setReader(ReaderConfig reader) {
        this.reader = reader;
    }

    public UdpListenerConfig getListener() {
        return listener;
    }

    public void setListener(UdpListenerConfig listener) {
        this.listener = listener;
    }

    public List<PayloadConfig> getMessages() {
        return messages;
    }

    public void setMessages(List<PayloadConfig> messages) {
        this.messages = messages;
    }

    public PayloadConfig getPayload() {
        return payload;
    }

    public void setPayload(PayloadConfig payload) {
        this.payload = payload;
    }

    public int getRepeat() {
        return repeat;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    public long getIntervalMillis() {
        return intervalMillis;
    }

    public void setIntervalMillis(long intervalMillis) {
        this.intervalMillis = intervalMillis;
    }

    public List<PayloadConfig> effectiveMessages() {
        if (messages != null && !messages.isEmpty()) {
            return messages;
        }

        if (payload != null) {
            return List.of(payload);
        }

        return List.of();
    }
}
