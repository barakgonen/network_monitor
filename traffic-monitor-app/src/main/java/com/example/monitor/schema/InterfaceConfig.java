package com.example.monitor.schema;

import java.util.List;

public class InterfaceConfig {
    private String key;
    private String name;
    private List<MessageConfig> messages;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<MessageConfig> getMessages() {
        return messages;
    }

    public void setMessages(List<MessageConfig> messages) {
        this.messages = messages;
    }
}
