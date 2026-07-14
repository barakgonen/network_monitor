package com.example.monitor.schema;

import java.util.List;

public class TrafficToolConfig {
    private List<InterfaceConfig> interfaces;
    private AutoReplyConfig autoReply = new AutoReplyConfig();

    public List<InterfaceConfig> getInterfaces() {
        return interfaces;
    }

    public void setInterfaces(List<InterfaceConfig> interfaces) {
        this.interfaces = interfaces;
    }

    public AutoReplyConfig getAutoReply() {
        return autoReply;
    }

    public void setAutoReply(AutoReplyConfig autoReply) {
        this.autoReply = autoReply;
    }
}
