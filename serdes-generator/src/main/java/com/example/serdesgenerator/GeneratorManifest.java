package com.example.serdesgenerator;

import java.util.List;

/**
 * SnakeYAML-bound shape of a generator manifest file: the list of root message classes (plus the
 * opcode each should be registered under) to fold into one output {@code *.protocol.json}.
 */
public class GeneratorManifest {
    private List<GeneratorManifestEntry> messages;

    public List<GeneratorManifestEntry> getMessages() {
        return messages;
    }

    public void setMessages(List<GeneratorManifestEntry> messages) {
        this.messages = messages;
    }
}
