package com.example.monitor.autoreply;

import com.example.monitor.schema.InterfaceConfig;
import com.example.monitor.schema.TrafficToolConfig;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AutoReplySettingsService {
    private volatile boolean globalEnabled;
    private final Map<String, InterfaceAutoReplySettings> byInterface = new ConcurrentHashMap<>();

    public AutoReplySettingsService(TrafficToolConfig config) {
        this.globalEnabled = config.getAutoReply().isEnabled();

        for (InterfaceConfig interfaceConfig : config.getInterfaces()) {
            byInterface.put(interfaceConfig.getName(), new InterfaceAutoReplySettings(
                    interfaceConfig.getAutoReply().isEnabled(),
                    interfaceConfig.getAutoReply().getHost(),
                    interfaceConfig.getAutoReply().getPort()
            ));
        }
    }

    public boolean isGlobalEnabled() {
        return globalEnabled;
    }

    public void setGlobalEnabled(boolean enabled) {
        this.globalEnabled = enabled;
    }

    public boolean shouldAutoReply(String interfaceName) {
        return globalEnabled && interfaceSettings(interfaceName)
                .map(InterfaceAutoReplySettings::enabled)
                .orElse(false);
    }

    public Optional<InterfaceAutoReplySettings> interfaceSettings(String interfaceName) {
        return Optional.ofNullable(byInterface.get(interfaceName));
    }

    public Map<String, InterfaceAutoReplySettings> allInterfaceSettings() {
        return Map.copyOf(byInterface);
    }

    public void updateInterfaceSettings(String interfaceName, boolean enabled, String host, int port) {
        byInterface.put(interfaceName, new InterfaceAutoReplySettings(enabled, host, port));
    }

    public record InterfaceAutoReplySettings(boolean enabled, String host, int port) {
    }
}
