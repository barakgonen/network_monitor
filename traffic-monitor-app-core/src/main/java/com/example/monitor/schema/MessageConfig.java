package com.example.monitor.schema;

public class MessageConfig {
    private String type;
    private String definitionClass;
    private String messageClass;
    private Integer opcode;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDefinitionClass() {
        return definitionClass;
    }

    public void setDefinitionClass(String definitionClass) {
        this.definitionClass = definitionClass;
    }

    /**
     * Fully-qualified class name of a message with its own {@code toByteArray}/{@code fromByteBuffer}-style
     * codec, wired reflectively via {@code ReflectiveMessageDefinition} instead of a hand-written
     * {@code MessageDefinition}. Mutually exclusive with {@link #getDefinitionClass()}; requires {@link #getOpcode()}.
     */
    public String getMessageClass() {
        return messageClass;
    }

    public void setMessageClass(String messageClass) {
        this.messageClass = messageClass;
    }

    public Integer getOpcode() {
        return opcode;
    }

    public void setOpcode(Integer opcode) {
        this.opcode = opcode;
    }
}
