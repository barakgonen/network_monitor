package com.example.tester.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScenarioLoaderTest {

    @TempDir
    Path tempDir;

    private final ScenarioLoader loader = new ScenarioLoader();

    @Test
    void load_withValidV2MessagesScenario_parsesUdpAndMessages() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                messages:
                  - mode: PING
                    ping:
                      sequence: 5
                repeat: 3
                intervalMillis: 500
                """);

        TesterScenario scenario = loader.load(file);

        assertThat(scenario.getUdp().getHost()).isEqualTo("127.0.0.1");
        assertThat(scenario.getUdp().getPort()).isEqualTo(5001);
        assertThat(scenario.effectiveMessages()).hasSize(1);
        assertThat(scenario.effectiveMessages().get(0).getMode()).isEqualTo(PayloadMode.PING);
        assertThat(scenario.getRepeat()).isEqualTo(3);
        assertThat(scenario.getIntervalMillis()).isEqualTo(500);
    }

    @Test
    void load_withLegacyV1PayloadScenario_parsesAsSingleMessage() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                payload:
                  mode: TEXT
                  text: hello
                """);

        TesterScenario scenario = loader.load(file);

        assertThat(scenario.effectiveMessages()).hasSize(1);
        assertThat(scenario.effectiveMessages().get(0).getMode()).isEqualTo(PayloadMode.TEXT);
        assertThat(scenario.effectiveMessages().get(0).getText()).isEqualTo("hello");
    }

    @Test
    void load_withMissingFile_throwsIllegalArgumentException() {
        Path missing = tempDir.resolve("does-not-exist.yml");

        assertThatThrownBy(() -> loader.load(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void load_withMissingUdpSection_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                messages:
                  - mode: PING
                    ping:
                      sequence: 1
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("udp");
    }

    @Test
    void load_withBlankUdpHost_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: ""
                  port: 5001
                payload:
                  mode: TEXT
                  text: hi
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("udp.host");
    }

    @Test
    void load_withInvalidUdpPort_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 70000
                payload:
                  mode: TEXT
                  text: hi
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid UDP port");
    }

    @Test
    void load_withNoMessagesOrPayload_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void load_withFruitOrangeModeMissingFruitSection_isValidBecauseFruitDefaultsAreNeverNull() throws Exception {
        // FruitPayloadConfig always has a non-null default instance (SnakeYAML never nulls it
        // out unless explicitly set to null in YAML), so this documents that the fruit-required
        // validation branch only trips when YAML explicitly sets `fruit: null`.
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                messages:
                  - mode: FRUIT_ORANGE
                    fruit: null
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fruit is required");
    }

    @Test
    void load_withWeatherModeMissingWeatherSection_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                messages:
                  - mode: WEATHER_TEMPERATURE_READING
                    weather: null
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weather is required");
    }

    @Test
    void load_withPingModeMissingPingSection_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                messages:
                  - mode: PING
                    ping: null
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ping is required");
    }

    @Test
    void load_withZeroRepeat_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                payload:
                  mode: TEXT
                  text: hi
                repeat: 0
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repeat");
    }

    @Test
    void load_withNegativeIntervalMillis_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                payload:
                  mode: TEXT
                  text: hi
                intervalMillis: -1
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalMillis");
    }

    @Test
    void load_withEnabledListenerAndInvalidListenerPort_throwsIllegalArgumentException() throws Exception {
        Path file = tempDir.resolve("scenario.yml");
        Files.writeString(file, """
                udp:
                  host: 127.0.0.1
                  port: 5001
                payload:
                  mode: TEXT
                  text: hi
                listener:
                  enabled: true
                  port: 0
                """);

        assertThatThrownBy(() -> loader.load(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("listener.port");
    }
}
