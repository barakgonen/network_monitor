package com.example.messagereader.api;

public class TrafficMessageDefinition {
    private String messageClass;
    private String displayName;

    public TrafficMessageDefinition() {
    }

    public TrafficMessageDefinition(String messageClass, String displayName) {
        this.messageClass = messageClass;
        this.displayName = displayName;
    }

    public String getMessageClass() {
        return messageClass;
    }

    public void setMessageClass(String messageClass) {
        this.messageClass = messageClass;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
