package com.example.monitor.api;

import com.example.monitor.autoreply.AutoReplySettingsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AutoReplyController {
    private final AutoReplySettingsService autoReplySettingsService;

    public AutoReplyController(AutoReplySettingsService autoReplySettingsService) {
        this.autoReplySettingsService = autoReplySettingsService;
    }

    @GetMapping("/api/autoreply/settings")
    public AutoReplySettingsResponse settings() {
        return toResponse();
    }

    @PostMapping("/api/autoreply/global")
    public AutoReplySettingsResponse updateGlobal(@RequestBody UpdateAutoReplyGlobalRequest request) {
        autoReplySettingsService.setGlobalEnabled(request.enabled());
        return toResponse();
    }

    @PostMapping("/api/autoreply/interface")
    public AutoReplySettingsResponse updateInterface(@RequestBody UpdateAutoReplyInterfaceRequest request) {
        autoReplySettingsService.updateInterfaceSettings(
                request.interfaceName(),
                request.enabled(),
                request.host(),
                request.port(),
                request.transport()
        );
        return toResponse();
    }

    private AutoReplySettingsResponse toResponse() {
        return new AutoReplySettingsResponse(
                autoReplySettingsService.isGlobalEnabled(),
                autoReplySettingsService.allInterfaceSettings()
        );
    }
}
