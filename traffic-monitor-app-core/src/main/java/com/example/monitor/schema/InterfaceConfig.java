package com.example.monitor.schema;

import java.nio.ByteOrder;
import java.util.List;

public class InterfaceConfig {
    public static final String DEFAULT_HEADER_TYPE = "com.example.schemacore.envelope.DefaultEnvelopeHeader";
    public static final String DEFAULT_OPCODE_FIELD_NAME = "opcode";
    public static final String DEFAULT_BODY_LENGTH_FIELD_NAME = "bodyLength";

    private String key;
    private String name;
    private List<MessageConfig> messages;
    private AutoReplyDestinationConfig autoReply = new AutoReplyDestinationConfig();

    /**
     * Every interface gets its own dedicated socket on {@link #getPort()}/{@link #getProtocol()}
     * (both mutable at runtime via {@code InterfaceRuntimeState.configure}, reset to these
     * config-file defaults on restart).
     */
    private boolean enabled = true;
    private String protocol = "UDP";
    private Integer port;

    /**
     * {@code "SERVER"} (default): bind {@link #getPort()} and listen, as before. {@code
     * "CLIENT"} (TCP only - UDP is connectionless and has no client/server distinction here):
     * connect out to {@link #getHost()}:{@link #getPort()} instead of binding, using the same
     * decode pipeline once connected. See {@link InterfaceModeValidator} for the validity rules.
     */
    private String mode = "SERVER";

    /**
     * The remote host to connect to when {@link #getMode()} is {@code "CLIENT"}; unused (and
     * left {@code null}) in {@code "SERVER"} mode, where {@link #getPort()} is a local bind
     * port rather than a remote one.
     */
    private String host;

    private String byteOrder = "BIG_ENDIAN";
    private String headerType = DEFAULT_HEADER_TYPE;
    private String opcodeFieldName = DEFAULT_OPCODE_FIELD_NAME;

    /**
     * {@code false} (default): the message class only ever handles body-only bytes, so the
     * pipeline strips the header before decoding and the publisher prepends one on encode
     * (matches {@link #DEFAULT_HEADER_TYPE}'s layout, e.g. fruit/weather/ping/candy).
     * {@code true}: the message class parses/emits its own header itself (e.g. rada), so the
     * full payload is passed through unchanged on both decode and encode.
     */
    private boolean messageOwnsHeader = false;

    /**
     * Header field holding the body length in bytes, used only to frame messages on a TCP byte
     * stream (UDP datagrams are already framed one-per-packet, so this is unused there).
     */
    private String bodyLengthFieldName = DEFAULT_BODY_LENGTH_FIELD_NAME;

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
     * This interface's own socket port (see {@link #getProtocol()}), decoded/encoded using
     * {@link #getHeaderType()}/{@link #getOpcodeFieldName()}. Every interface must configure
     * one ({@link com.example.monitor.schema.TrafficToolConfigLoader} fails fast at startup
     * otherwise) - {@link #hasDedicatedPort()} exists purely for that validation, not as a
     * runtime branch.
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

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getByteOrder() {
        return byteOrder;
    }

    public void setByteOrder(String byteOrder) {
        this.byteOrder = byteOrder;
    }

    /**
     * This interface's default order, used to decode the header itself (before the message
     * type - and thus any per-message {@link MessageConfig#getByteOrder()} override - is even
     * known) and as the fallback for messages that don't set their own override. See
     * {@link #parseByteOrder(String, String)} for the shared string-to-{@link ByteOrder} rule.
     */
    public ByteOrder resolveByteOrder() {
        return parseByteOrder(byteOrder, "interface " + key);
    }

    public static ByteOrder parseByteOrder(String raw, String context) {
        if ("BIG_ENDIAN".equals(raw)) {
            return ByteOrder.BIG_ENDIAN;
        }
        if ("LITTLE_ENDIAN".equals(raw)) {
            return ByteOrder.LITTLE_ENDIAN;
        }
        throw new IllegalArgumentException(
                "Invalid byteOrder '" + raw + "' for " + context + ". Expected BIG_ENDIAN or LITTLE_ENDIAN.");
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

    public boolean isMessageOwnsHeader() {
        return messageOwnsHeader;
    }

    public void setMessageOwnsHeader(boolean messageOwnsHeader) {
        this.messageOwnsHeader = messageOwnsHeader;
    }

    public String getBodyLengthFieldName() {
        return bodyLengthFieldName;
    }

    public void setBodyLengthFieldName(String bodyLengthFieldName) {
        this.bodyLengthFieldName = bodyLengthFieldName;
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
