package com.example.tester.config;

import com.example.messagereader.api.TrafficInterfaceDefinition;

import java.util.ArrayList;
import java.util.List;

public class ReaderConfig {
    private boolean enabled = true;
    private int bufferSizeBytes = 65507;
    private int durationSeconds = 120;
    private List<TrafficInterfaceDefinition> interfaces = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBufferSizeBytes() {
        return bufferSizeBytes;
    }

    public void setBufferSizeBytes(int bufferSizeBytes) {
        this.bufferSizeBytes = bufferSizeBytes;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public List<TrafficInterfaceDefinition> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<TrafficInterfaceDefinition> interfaces) {
        this.interfaces = interfaces;
    }
}
