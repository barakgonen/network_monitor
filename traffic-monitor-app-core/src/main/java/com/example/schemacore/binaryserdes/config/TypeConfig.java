package com.example.schemacore.binaryserdes.config;

import java.util.List;

public class TypeConfig {
    public String name;
    public String kind;   // "uint16","uint32","fixedString","record","array",...
    public Integer length; // for fixedString, and element count for array

    public List<FieldConfig> fields; // for record
    public String elementType; // for array - references another type's name (built-in or custom)
}
