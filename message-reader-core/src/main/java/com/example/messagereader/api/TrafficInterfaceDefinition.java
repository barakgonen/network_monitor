package com.example.messagereader.api;

import java.util.LinkedHashMap;
import java.util.Map;

public class TrafficInterfaceDefinition {
    private String name;
    private boolean enabled = true;
    private TransportProtocol protocol = TransportProtocol.UDP;
    private int port;
    private String byteOrder = "BIG_ENDIAN";
    private String headerType;
    private String opcodeFieldName;
    private Map<String, TrafficMessageDefinition> supportedMessages = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TransportProtocol getProtocol() {
        return protocol;
    }

    public void setProtocol(TransportProtocol protocol) {
        this.protocol = protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol == null || protocol.isBlank()
                ? TransportProtocol.UDP
                : TransportProtocol.valueOf(protocol.trim().toUpperCase());
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
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

    public Map<String, TrafficMessageDefinition> getSupportedMessages() {
        return supportedMessages;
    }

    public void setSupportedMessages(Map<String, TrafficMessageDefinition> supportedMessages) {
        this.supportedMessages = supportedMessages;
    }
}
