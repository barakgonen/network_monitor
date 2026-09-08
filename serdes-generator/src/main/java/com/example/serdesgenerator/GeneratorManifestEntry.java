package com.example.serdesgenerator;

public class GeneratorManifestEntry {
    private String className;
    private long opcode;

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public long getOpcode() {
        return opcode;
    }

    public void setOpcode(long opcode) {
        this.opcode = opcode;
    }
}
