package com.example.destination.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DestinationConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final DestinationConfigLoader loader = new DestinationConfigLoader();

    @Test
    void load_withValidConfig_parsesInterfaces() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: fruit
                    protocol: UDP
                    port: 8001
                    replyMode: NONE
                  - key: ping
                    protocol: UDP
                    port: 8002
                    replyMode: ECHO
                """);

        DestinationConfig config = loader.load(file);

        assertThat(config.getInterfaces()).hasSize(2);
        assertThat(config.getInterfaces().get(0).getReplyMode()).isEqualTo(ReplyMode.NONE);
        assertThat(config.getInterfaces().get(1).getReplyMode()).isEqualTo(ReplyMode.ECHO);
    }

    @Test
    void load_withDefaultReplyMode_defaultsToNone() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: fruit
                    protocol: UDP
                    port: 8001
                """);

        DestinationConfig config = loader.load(file);

        assertThat(config.getInterfaces().get(0).getReplyMode()).isEqualTo(ReplyMode.NONE);
    }

    @Test
    void load_withMissingFile_throws() {
        assertThatThrownBy(() -> loader.load(tempDir.resolve("does-not-exist.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void load_withEmptyInterfaces_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, "interfaces: []\n");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one interface");
    }

    @Test
    void load_withInvalidProtocol_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: fruit
                    protocol: FTP
                    port: 8001
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void load_withInvalidPort_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: fruit
                    protocol: UDP
                    port: 0
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port is invalid");
    }

    @Test
    void load_withPongReplyModeOnNonUdpProtocol_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: pets
                    protocol: REST
                    port: 8060
                    replyMode: PONG
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PONG is only valid for protocol UDP");
    }

    @Test
    void load_withPongReplyModeOnUdp_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: ping
                    protocol: UDP
                    port: 8003
                    replyMode: PONG
                """);

        DestinationConfig config = loader.load(file);

        assertThat(config.getInterfaces().get(0).getReplyMode()).isEqualTo(ReplyMode.PONG);
    }

    @Test
    void load_withReplyPortOnUdp_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: ping
                    protocol: UDP
                    port: 8003
                    replyMode: PONG
                    replyPort: 6103
                """);

        DestinationConfig config = loader.load(file);

        assertThat(config.getInterfaces().get(0).getReplyPort()).isEqualTo(6103);
    }

    @Test
    void load_withReplyPortOnNonUdpProtocol_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: pets
                    protocol: REST
                    port: 8060
                    replyPort: 6103
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyPort is only valid for protocol UDP");
    }

    @Test
    void load_withInvalidReplyPort_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: ping
                    protocol: UDP
                    port: 8003
                    replyPort: 0
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyPort is invalid");
    }

    @Test
    void load_withClientModeAndHost_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: candy-reverse
                    protocol: TCP
                    port: 8104
                    replyMode: ECHO
                    mode: CLIENT
                    host: 127.0.0.1
                """);

        DestinationConfig config = loader.load(file);

        InterfaceEntry entry = config.getInterfaces().get(0);
        assertThat(entry.getMode()).isEqualTo("CLIENT");
        assertThat(entry.getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    void load_withClientModeAndNoHost_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: candy-reverse
                    protocol: TCP
                    port: 8104
                    mode: CLIENT
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host is required when mode is CLIENT");
    }

    @Test
    void load_withModeOnNonTcpProtocol_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: ping
                    protocol: UDP
                    port: 8003
                    mode: CLIENT
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode/host are only valid for protocol TCP");
    }

    @Test
    void load_withInvalidMode_throws() throws Exception {
        Path file = tempDir.resolve("destination-interfaces.yml");
        Files.writeString(file, """
                interfaces:
                  - key: candy
                    protocol: TCP
                    port: 8004
                    mode: BOGUS
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mode must be one of");
    }
}
