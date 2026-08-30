package com.example.schemacore.binaryserdes.config;

import java.util.List;

public class TypeConfig {
    public String name;
    public String kind;   // "uint16","uint32","fixedString","lengthPrefixedString"
    public Integer length; // for fixedString

    public List<FieldConfig> fields;
}
