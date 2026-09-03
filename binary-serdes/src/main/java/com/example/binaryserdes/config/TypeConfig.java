package com.example.binaryserdes.config;

import java.util.List;
import java.util.Map;

public class TypeConfig {
    public String name;
    public String kind;   // "uint16","uint32","fixedString","record","array","enum",...
    public Integer length; // for fixedString, and element count for array

    public List<FieldConfig> fields; // for record
    public String elementType; // for array - references another type's name (built-in or custom)

    public String underlyingType; // for enum - references a numeric type's name (built-in or custom)
    public Map<String, Integer> values; // for enum - symbolic name -> wire code
}
