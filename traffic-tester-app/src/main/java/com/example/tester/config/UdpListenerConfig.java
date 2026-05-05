package com.example.tester.config;

public class UdpListenerConfig {
    private boolean enabled = true;
    private int port = 7001;
    private int durationSeconds = 120;
    private int bufferSizeBytes = 65507;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public int getBufferSizeBytes() {
        return bufferSizeBytes;
    }

    public void setBufferSizeBytes(int bufferSizeBytes) {
        this.bufferSizeBytes = bufferSizeBytes;
    }
}
