package com.example.tester.config;

public class TesterScenario {
    private UdpConfig udp;
    private PayloadConfig payload;
    private int repeat = 1;
    private long intervalMillis = 1000;

    public UdpConfig getUdp() {
        return udp;
    }

    public void setUdp(UdpConfig udp) {
        this.udp = udp;
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
}
