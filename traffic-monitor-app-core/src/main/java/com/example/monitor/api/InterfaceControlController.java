package com.example.monitor.api;

import com.example.monitor.interfaces.InterfaceControlService;
import com.example.monitor.interfaces.InterfaceStatusDto;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InterfaceControlController {
    private final InterfaceControlService interfaceControlService;

    public InterfaceControlController(InterfaceControlService interfaceControlService) {
        this.interfaceControlService = interfaceControlService;
    }

    @GetMapping("/api/interfaces")
    public List<InterfaceStatusDto> list() {
        return interfaceControlService.statuses();
    }

    @PostMapping("/api/interfaces/{key}/start")
    public List<InterfaceStatusDto> start(@PathVariable("key") String key) {
        interfaceControlService.start(key);
        return interfaceControlService.statuses();
    }

    @PostMapping("/api/interfaces/{key}/stop")
    public List<InterfaceStatusDto> stop(@PathVariable("key") String key) {
        interfaceControlService.stop(key);
        return interfaceControlService.statuses();
    }
}
