package com.example.serdesgenerator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorManifestLoaderTest {

    private final GeneratorManifestLoader loader = new GeneratorManifestLoader();

    @Test
    void loadsMessagesInOrder(@TempDir Path tempDir) throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yml");
        Files.writeString(manifestPath, """
                messages:
                  - className: com.example.tester.schemas.rada.messages.RadaStatus
                    opcode: 3
                  - className: com.example.tester.schemas.rada.messages.RadaExtendedStatus
                    opcode: 1
                """);

        GeneratorManifest manifest = loader.load(manifestPath);

        assertThat(manifest.getMessages()).hasSize(2);
        assertThat(manifest.getMessages().get(0).getClassName())
                .isEqualTo("com.example.tester.schemas.rada.messages.RadaStatus");
        assertThat(manifest.getMessages().get(0).getOpcode()).isEqualTo(3);
        assertThat(manifest.getMessages().get(1).getOpcode()).isEqualTo(1);
    }

    @Test
    void missingFileThrows(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist.yml");

        assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void emptyMessagesListThrows(@TempDir Path tempDir) throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yml");
        Files.writeString(manifestPath, "messages: []\n");

        assertThatThrownBy(() -> loader.load(manifestPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one message");
    }

    @Test
    void missingClassNameThrows(@TempDir Path tempDir) throws Exception {
        Path manifestPath = tempDir.resolve("manifest.yml");
        Files.writeString(manifestPath, """
                messages:
                  - opcode: 3
                """);

        assertThatThrownBy(() -> loader.load(manifestPath))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("className");
    }
}
