package com.example.monitor.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class InterfaceCatalogController {

    private final InterfaceCatalogService interfaceCatalogService;

    public InterfaceCatalogController(InterfaceCatalogService interfaceCatalogService) {
        this.interfaceCatalogService = interfaceCatalogService;
    }

    @GetMapping("/api/interfaces/catalog")
    public List<InterfaceCatalogEntryDto> catalog() {
        return interfaceCatalogService.list();
    }
}
