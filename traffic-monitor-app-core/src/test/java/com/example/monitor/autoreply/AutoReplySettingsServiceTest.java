package com.example.monitor.autoreply;

import com.example.monitor.schema.AutoReplyConfig;
import com.example.monitor.schema.AutoReplyDestinationConfig;
import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoReplySettingsServiceTest {

    @Test
    void constructor_seedsFromTrafficToolConfig() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host-a", 1111, "UDP");

        assertThat(service.isGlobalEnabled()).isTrue();
        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "host-a", 1111, "UDP"));
    }

    @Test
    void constructor_withNullTransport_defaultsToUdp() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host-a", 1111, null);

        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "host-a", 1111, "UDP"));
    }

    @Test
    void constructor_withExplicitTcpTransport_preservesIt() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host-a", 1111, "TCP");

        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "host-a", 1111, "TCP"));
    }

    @Test
    void shouldAutoReply_whenGlobalEnabledAndInterfaceEnabled_returnsTrue() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        assertThat(service.shouldAutoReply("Fruit Interface")).isTrue();
    }

    @Test
    void shouldAutoReply_whenGlobalDisabled_returnsFalseRegardlessOfInterfaceSetting() {
        AutoReplySettingsService service = newService(false, "Fruit Interface", true, "host", 1, "UDP");

        assertThat(service.shouldAutoReply("Fruit Interface")).isFalse();
    }

    @Test
    void shouldAutoReply_whenInterfaceDisabled_returnsFalseRegardlessOfGlobalSetting() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", false, "host", 1, "UDP");

        assertThat(service.shouldAutoReply("Fruit Interface")).isFalse();
    }

    @Test
    void shouldAutoReply_whenInterfaceNotConfigured_returnsFalse() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        assertThat(service.shouldAutoReply("Weather Interface")).isFalse();
    }

    @Test
    void setGlobalEnabled_updatesGlobalFlag() {
        AutoReplySettingsService service = newService(false, "Fruit Interface", true, "host", 1, "UDP");

        service.setGlobalEnabled(true);

        assertThat(service.isGlobalEnabled()).isTrue();
    }

    @Test
    void updateInterfaceSettings_forNewInterfaceName_addsEntry() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        service.updateInterfaceSettings("Weather Interface", true, "weather-host", 2222, "UDP");

        assertThat(service.interfaceSettings("Weather Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "weather-host", 2222, "UDP"));
    }

    @Test
    void updateInterfaceSettings_forExistingInterfaceName_overwritesEntry() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        service.updateInterfaceSettings("Fruit Interface", false, "new-host", 9999, "UDP");

        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(false, "new-host", 9999, "UDP"));
    }

    @Test
    void updateInterfaceSettings_withTcpTransport_storesTcp() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        service.updateInterfaceSettings("Fruit Interface", true, "host", 1, "TCP");

        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "host", 1, "TCP"));
    }

    @Test
    void updateInterfaceSettings_withNullTransport_defaultsToUdp() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "TCP");

        service.updateInterfaceSettings("Fruit Interface", true, "host", 1, null);

        assertThat(service.interfaceSettings("Fruit Interface")).contains(
                new AutoReplySettingsService.InterfaceAutoReplySettings(true, "host", 1, "UDP"));
    }

    @Test
    void updateInterfaceSettings_withInvalidTransport_throwsIllegalArgumentException() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        assertThatThrownBy(() -> service.updateInterfaceSettings("Fruit Interface", true, "host", 1, "CARRIER_PIGEON"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void interfaceSettings_returnsEmptyOptionalForUnknownInterface() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        assertThat(service.interfaceSettings("Unknown Interface")).isEmpty();
    }

    @Test
    void allInterfaceSettings_returnsImmutableSnapshotUnaffectedByLaterUpdates() {
        AutoReplySettingsService service = newService(true, "Fruit Interface", true, "host", 1, "UDP");

        var snapshot = service.allInterfaceSettings();
        service.updateInterfaceSettings("Weather Interface", true, "other-host", 3, "UDP");

        assertThat(snapshot).doesNotContainKey("Weather Interface");
        assertThat(service.allInterfaceSettings()).containsKey("Weather Interface");
    }

    private static AutoReplySettingsService newService(
            boolean globalEnabled, String interfaceName, boolean interfaceEnabled, String host, int port, String transport) {
        AutoReplyConfig autoReplyConfig = new AutoReplyConfig();
        autoReplyConfig.setEnabled(globalEnabled);

        AutoReplyDestinationConfig destinationConfig = new AutoReplyDestinationConfig();
        destinationConfig.setEnabled(interfaceEnabled);
        destinationConfig.setHost(host);
        destinationConfig.setPort(port);
        destinationConfig.setTransport(transport);

        InterfaceConfig interfaceConfig = new InterfaceConfig();
        interfaceConfig.setKey(interfaceName.toLowerCase());
        interfaceConfig.setName(interfaceName);
        interfaceConfig.setAutoReply(destinationConfig);

        TrafficToolConfig config = new TrafficToolConfig();
        config.setAutoReply(autoReplyConfig);
        config.setInterfaces(List.of(interfaceConfig));

        return new AutoReplySettingsService(config);
    }
}
