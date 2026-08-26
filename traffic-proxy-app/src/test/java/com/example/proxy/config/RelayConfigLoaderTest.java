package com.example.proxy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayConfigLoaderTest {

    @TempDir
    Path tempDir;

    private final RelayConfigLoader loader = new RelayConfigLoader();

    @Test
    void load_withValidConfig_parsesAllEndpoints() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6001 }
                    destination: { host: 127.0.0.1, port: 8001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                """);

        ProxyRelayConfig config = loader.load(file);

        assertThat(config.getRelays()).hasSize(1);
        RelayEntry entry = config.getRelays().get(0);
        assertThat(entry.getKey()).isEqualTo("fruit");
        assertThat(entry.getProtocol()).isEqualTo("UDP");
        assertThat(entry.getListen().getPort()).isEqualTo(6001);
        assertThat(entry.getDestination().getPort()).isEqualTo(8001);
        assertThat(entry.getMirror().getPort()).isEqualTo(5001);
    }

    @Test
    void load_withMissingFile_throws() {
        assertThatThrownBy(() -> loader.load(tempDir.resolve("does-not-exist.yml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void load_withEmptyRelays_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, "relays: []\n");

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one relay");
    }

    @Test
    void load_withInvalidProtocol_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: FTP
                    listen: { host: 0.0.0.0, port: 6001 }
                    destination: { host: 127.0.0.1, port: 8001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void load_withMissingDestination_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("destination");
    }

    @Test
    void load_withListenPortEqualToDestinationPort_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6001 }
                    destination: { host: 127.0.0.1, port: 6001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listen.port must not equal destination.port");
    }

    @Test
    void load_withDuplicateListenPorts_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6001 }
                    destination: { host: 127.0.0.1, port: 8001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                  - key: ping
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6001 }
                    destination: { host: 127.0.0.1, port: 8002 }
                    mirror: { host: 127.0.0.1, port: 5002 }
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate listen.port");
    }

    @Test
    void load_withInvalidPort_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: fruit
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 70000 }
                    destination: { host: 127.0.0.1, port: 8001 }
                    mirror: { host: 127.0.0.1, port: 5001 }
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port is invalid");
    }

    @Test
    void load_withReplyPortOnUdp_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: ping
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6003 }
                    destination: { host: 127.0.0.1, port: 8003 }
                    mirror: { host: 127.0.0.1, port: 5003 }
                    replyPort: 6103
                """);

        ProxyRelayConfig config = loader.load(file);

        assertThat(config.getRelays().get(0).getReplyPort()).isEqualTo(6103);
    }

    @Test
    void load_withReplyPortOnNonUdpProtocol_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy
                    protocol: TCP
                    listen: { host: 0.0.0.0, port: 6004 }
                    destination: { host: 127.0.0.1, port: 8004 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                    replyPort: 6104
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyPort is only valid for protocol UDP");
    }

    @Test
    void load_withReplyPortEqualToListenPort_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: ping
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6003 }
                    destination: { host: 127.0.0.1, port: 8003 }
                    mirror: { host: 127.0.0.1, port: 5003 }
                    replyPort: 6003
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyPort must not equal listen.port");
    }

    @Test
    void load_withInvalidReplyPort_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: ping
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6003 }
                    destination: { host: 127.0.0.1, port: 8003 }
                    mirror: { host: 127.0.0.1, port: 5003 }
                    replyPort: 0
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("replyPort is invalid");
    }

    @Test
    void load_withReverseTcpModes_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy-reverse
                    protocol: TCP
                    listen: { host: 127.0.0.1, port: 7004 }
                    destination: { host: 0.0.0.0, port: 8104 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                    listenMode: CLIENT
                    destinationMode: SERVER
                """);

        ProxyRelayConfig config = loader.load(file);

        RelayEntry entry = config.getRelays().get(0);
        assertThat(entry.getListenMode()).isEqualTo("CLIENT");
        assertThat(entry.getDestinationMode()).isEqualTo("SERVER");
    }

    @Test
    void load_withDefaultTcpModes_parsesSuccessfully() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy
                    protocol: TCP
                    listen: { host: 0.0.0.0, port: 6004 }
                    destination: { host: 127.0.0.1, port: 8004 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                """);

        ProxyRelayConfig config = loader.load(file);

        RelayEntry entry = config.getRelays().get(0);
        assertThat(entry.getListenMode()).isEqualTo("SERVER");
        assertThat(entry.getDestinationMode()).isEqualTo("CLIENT");
    }

    @Test
    void load_withBothServerTcpModes_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy
                    protocol: TCP
                    listen: { host: 0.0.0.0, port: 6004 }
                    destination: { host: 127.0.0.1, port: 8004 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                    listenMode: SERVER
                    destinationMode: SERVER
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of listenMode/destinationMode must be SERVER");
    }

    @Test
    void load_withBothClientTcpModes_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy
                    protocol: TCP
                    listen: { host: 0.0.0.0, port: 6004 }
                    destination: { host: 127.0.0.1, port: 8004 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                    listenMode: CLIENT
                    destinationMode: CLIENT
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one of listenMode/destinationMode must be SERVER");
    }

    @Test
    void load_withTcpModeOnNonTcpProtocol_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: ping
                    protocol: UDP
                    listen: { host: 0.0.0.0, port: 6003 }
                    destination: { host: 127.0.0.1, port: 8003 }
                    mirror: { host: 127.0.0.1, port: 5003 }
                    listenMode: CLIENT
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listenMode/destinationMode are only valid for protocol TCP");
    }

    @Test
    void load_withInvalidTcpMode_throws() throws Exception {
        Path file = tempDir.resolve("proxy-relays.yml");
        Files.writeString(file, """
                relays:
                  - key: candy
                    protocol: TCP
                    listen: { host: 0.0.0.0, port: 6004 }
                    destination: { host: 127.0.0.1, port: 8004 }
                    mirror: { host: 127.0.0.1, port: 5004 }
                    listenMode: BOGUS
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listenMode must be one of");
    }
}
