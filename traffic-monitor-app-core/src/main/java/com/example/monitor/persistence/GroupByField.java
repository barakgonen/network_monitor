package com.example.monitor.persistence;

public enum GroupByField {
    INTERFACE_NAME("interface_name"),
    MESSAGE_TYPE("message_type");

    private final String columnName;

    GroupByField(String columnName) {
        this.columnName = columnName;
    }

    public String columnName() {
        return columnName;
    }
}
