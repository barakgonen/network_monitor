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
                    protocol: UDP
                    port: 5001
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
                    protocol: UDP
                    port: 5001
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
                    protocol: UDP
                    port: 5001
                    messages:
                      - type: Orange
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("definitionClass");
    }

    @Test
    void load_withInterfaceMissingPort_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("missing-port.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must define a port");
    }

    @Test
    void load_withValidClientModeInterface_parsesModeAndHost() throws Exception {
        Path configFile = tempDir.resolve("client-mode.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    protocol: TCP
                    port: 5001
                    mode: CLIENT
                    host: remote-host
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                """);

        TrafficToolConfig config = loader.load(configFile);

        InterfaceConfig fruit = config.getInterfaces().get(0);
        assertThat(fruit.getMode()).isEqualTo("CLIENT");
        assertThat(fruit.getHost()).isEqualTo("remote-host");
    }

    @Test
    void load_withClientModeAndUdpProtocol_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("client-mode-udp.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    protocol: UDP
                    port: 5001
                    mode: CLIENT
                    host: remote-host
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol=TCP");
    }

    @Test
    void load_withClientModeAndMissingHost_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("client-mode-missing-host.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    protocol: TCP
                    port: 5001
                    mode: CLIENT
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank host");
    }

    @Test
    void load_withMessageLevelByteOrder_parsesOverride_andLeavesOtherMessagesNull() throws Exception {
        Path configFile = tempDir.resolve("message-byte-order.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: rada
                    name: Rada Interface
                    protocol: UDP
                    port: 5050
                    byteOrder: BIG_ENDIAN
                    messages:
                      - type: RadaStatus
                        messageClass: com.example.schemas.rada.messages.RadaStatus
                        opcode: 3
                      - type: RadaTracksExtended
                        messageClass: com.example.schemas.rada.messages.RadaTracksExtended
                        opcode: 4
                        byteOrder: LITTLE_ENDIAN
                """);

        TrafficToolConfig config = loader.load(configFile);

        InterfaceConfig rada = config.getInterfaces().get(0);
        assertThat(rada.getByteOrder()).isEqualTo("BIG_ENDIAN");
        assertThat(rada.getMessages().get(0).getByteOrder()).isNull();
        assertThat(rada.getMessages().get(1).getByteOrder()).isEqualTo("LITTLE_ENDIAN");
    }

    @Test
    void load_withValidSerdesFile_parsesInterfaceWithoutRequiringMessages() throws Exception {
        Path serdesFile = tempDir.resolve("candy.protocol.json");
        Files.writeString(serdesFile, """
                {
                  "messages": [
                    { "name": "Candy", "opcode": 4001, "fields": [
                      { "name": "name", "type": "string" },
                      { "name": "calories", "type": "double64" }
                    ] }
                  ]
                }
                """);

        Path configFile = tempDir.resolve("serdes-interface.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: candy
                    name: Candy Interface
                    protocol: TCP
                    port: 5004
                    serdesFile: %s
                """.formatted(serdesFile.toString().replace("\\", "\\\\")));

        TrafficToolConfig config = loader.load(configFile);

        InterfaceConfig candy = config.getInterfaces().get(0);
        assertThat(candy.getSerdesFile()).isEqualTo(serdesFile.toString());
        assertThat(candy.getMessages()).isNull();
    }

    @Test
    void load_withMessageOwnsHeaderAndSerdesFileButNoSerdesHeaderType_throwsIllegalArgumentException() throws Exception {
        Path serdesFile = tempDir.resolve("rada.protocol.json");
        Files.writeString(serdesFile, """
                { "messages": [] }
                """);

        Path configFile = tempDir.resolve("missing-serdes-header-type.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: rada
                    name: Rada Interface
                    protocol: UDP
                    port: 5050
                    messageOwnsHeader: true
                    serdesFile: %s
                """.formatted(serdesFile.toString().replace("\\", "\\\\")));

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serdesHeaderType");
    }

    @Test
    void load_withSerdesFileThatDoesNotExist_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("missing-serdes-file.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: candy
                    name: Candy Interface
                    protocol: TCP
                    port: 5004
                    serdesFile: does-not-exist.protocol.json
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("serdesFile does not exist");
    }

    @Test
    void load_withInvalidModeString_throwsIllegalArgumentException() throws Exception {
        Path configFile = tempDir.resolve("invalid-mode.yml");
        Files.writeString(configFile, """
                autoReply:
                  enabled: false
                interfaces:
                  - key: fruit
                    name: Fruit Interface
                    protocol: UDP
                    port: 5001
                    mode: BOGUS
                    messages:
                      - type: Orange
                        definitionClass: com.example.schemas.fruit.OrangeMessageDefinition
                """);

        assertThatThrownBy(() -> loader.load(configFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SERVER or CLIENT");
    }
}
