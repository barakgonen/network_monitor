package com.example.monitor.schema;

import java.util.List;

public class InterfaceConfig {
    public static final String DEFAULT_HEADER_TYPE = "com.example.schemacore.DefaultEnvelopeHeader";
    public static final String DEFAULT_OPCODE_FIELD_NAME = "opcode";

    private String key;
    private String name;
    private List<MessageConfig> messages;
    private AutoReplyDestinationConfig autoReply = new AutoReplyDestinationConfig();

    /**
     * Transport fields below are only used when {@link #getPort()} is set, which opts this
     * interface into its own dedicated socket + header type instead of the legacy shared
     * fruit/weather ports and global opcode registry.
     */
    private boolean enabled = true;
    private String protocol = "UDP";
    private Integer port;
    private String byteOrder = "BIG_ENDIAN";
    private String headerType = DEFAULT_HEADER_TYPE;
    private String opcodeFieldName = DEFAULT_OPCODE_FIELD_NAME;

    /**
     * When set, the publisher fans a UDP send out to every "host:port" target here in addition
     * to (or instead of, depending on the request) the caller-specified destination.
     */
    private boolean shouldBroadcast = false;
    private List<String> broadcastTargets = List.of();

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

    public AutoReplyDestinationConfig getAutoReply() {
        return autoReply;
    }

    public void setAutoReply(AutoReplyDestinationConfig autoReply) {
        this.autoReply = autoReply;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    /**
     * When set, this interface is served on its own socket (see {@link #getProtocol()}) using
     * {@link #getHeaderType()}/{@link #getOpcodeFieldName()} instead of the legacy shared ports.
     */
    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public boolean hasDedicatedPort() {
        return port != null;
    }

    public String getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(String byteOrder) {
        this.byteOrder = byteOrder;
    }

    public String getHeaderType() {
        return headerType;
    }

    public void setHeaderType(String headerType) {
        this.headerType = headerType;
    }

    public String getOpcodeFieldName() {
        return opcodeFieldName;
    }

    public void setOpcodeFieldName(String opcodeFieldName) {
        this.opcodeFieldName = opcodeFieldName;
    }

    public boolean isShouldBroadcast() {
        return shouldBroadcast;
    }

    public void setShouldBroadcast(boolean shouldBroadcast) {
        this.shouldBroadcast = shouldBroadcast;
    }

    public List<String> getBroadcastTargets() {
        return broadcastTargets;
    }

    public void setBroadcastTargets(List<String> broadcastTargets) {
        this.broadcastTargets = broadcastTargets;
    }
}
