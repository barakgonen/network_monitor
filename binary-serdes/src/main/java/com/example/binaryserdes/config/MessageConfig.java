package com.example.binaryserdes.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

public class MessageConfig {
    public String name;

    /**
     * {@code null} means "not configured" - see {@link com.example.binaryserdes.MessageType#hasOpcode()}.
     * Raw 64-bit wire bit pattern; see {@link UnsignedLongDeserializer} for how literals in the
     * unsigned-only upper half of the range (&gt;= 2^63) are written in protocol JSON.
     */
    @JsonDeserialize(using = UnsignedLongDeserializer.class)
    public Long opcode;

    public List<FieldConfig> fields;
}
