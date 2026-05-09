package com.example.monitor.api;

import com.example.monitor.interfaces.InterfaceControlService;
import com.example.monitor.interfaces.InterfaceStatusDto;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/interfaces")
public class InterfaceControlController {
    private final InterfaceControlService interfaceControlService;

    public InterfaceControlController(InterfaceControlService interfaceControlService) {
        this.interfaceControlService = interfaceControlService;
    }

    @GetMapping
    public List<InterfaceStatusDto> interfaces() {
        return interfaceControlService.statuses();
    }

    @PostMapping("/{interfaceName}/start")
    public InterfaceStatusDto start(@PathVariable String interfaceName) {
        return interfaceControlService.start(interfaceName);
    }

    @PostMapping("/{interfaceName}/stop")
    public InterfaceStatusDto stop(@PathVariable String interfaceName) {
        return interfaceControlService.stop(interfaceName);
    }
}
