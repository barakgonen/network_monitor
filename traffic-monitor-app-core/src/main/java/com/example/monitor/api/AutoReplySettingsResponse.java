package com.example.monitor.api;

import com.example.monitor.autoreply.AutoReplySettingsService;

import java.util.Map;

public record AutoReplySettingsResponse(
        boolean globalEnabled,
        Map<String, AutoReplySettingsService.InterfaceAutoReplySettings> interfaces
) {
}
