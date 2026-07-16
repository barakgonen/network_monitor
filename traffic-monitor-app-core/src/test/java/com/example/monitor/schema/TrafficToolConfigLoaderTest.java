package com.example.monitor.schema;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrafficToolConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final TrafficToolConfigLoader loader = new TrafficToolConfigLoader();

    @Test
    void load_withValidYamlFile_parsesInterfacesAndAutoReplySettings() throws Exception {
        Path configFile = tempDir.resolve("valid.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: true

                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                    autoReply:
                      enabled: true
                      host: localhost
                      port: 7001
                """);

        TrafficToolConfig config = loader.load(configFile);

        assertThat(config.getAutoReply().isEnabled()).isTrue();
        assertThat(config.getInterfaces()).hasSize(1);
        assertThat(config.getInterfaces().get(0).getName()).isEqualTo("Fruit Interface");
        assertThat(config.getInterfaces().get(0).getMessages()).hasSize(1);
        assertThat(config.getInterfaces().get(0).getMessages().get(0).getDefinitionClass())
                .isEqualTo("com.example.schemas.fruit.OrangeMessageDefinition");
    }

    @Test
    void load_withMissingFile_throwsIllegalArgumentException() {
        Path missing = tempDir.resolve("does-not-exist.yml");

        assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void load_withNoInterfacesDefined_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("no-interfaces.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces: []
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one interface");
    }

    @Test
    void load_withInterfaceHavingNoMessages_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("no-messages.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    messages: []
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one message");
    }

    @Test
    void load_withMessageMissingDefinitionClass_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("missing-definition-class.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    messages:
                      - type: Orange
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionClass");
    }
}
