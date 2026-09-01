package com.example.binaryserdes;

/**
 * A single field in a MessageType schema.
 */
public final class MessageField<T> {

    private final String fieldName;
    private final Type<T> type;

    public MessageField(String fieldName, Type<T> type) {
        this.fieldName = fieldName;
        this.type = type;
    }

    public String getFieldName() {
        return fieldName;
    }

    public Type<T> getType() {
        return type;
    }
}
