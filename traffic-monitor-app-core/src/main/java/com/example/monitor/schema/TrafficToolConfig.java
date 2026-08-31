package com.example.monitor.schema;

import java.util.List;

public class TrafficToolConfig {
    private List<InterfaceConfig> interfaces;

    public List<InterfaceConfig> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<InterfaceConfig> interfaces) {
        this.interfaces = interfaces;
    }
}
