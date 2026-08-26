package com.example.destination.config;

public class InterfaceEntry {
    private String key;
    private String protocol;
    private Integer port;
    private ReplyMode replyMode = ReplyMode.NONE;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public ReplyMode getReplyMode() {
        return replyMode;
    }

    public void setReplyMode(ReplyMode replyMode) {
        this.replyMode = replyMode;
    }
}
