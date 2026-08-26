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
}
