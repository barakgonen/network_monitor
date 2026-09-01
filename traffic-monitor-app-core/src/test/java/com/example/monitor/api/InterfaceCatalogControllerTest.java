package com.example.monitor.api;

import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc setup (no {@code @WebMvcTest} in Spring Boot 4, see {@code AutoReplyControllerTest}). */
class InterfaceCatalogControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InterfaceConfig pets = new InterfaceConfig();
        pets.setKey("pets");
        pets.setName("Pets REST Interface");
        pets.setPort(5060);
        pets.setProtocol("REST");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(pets));

        InterfaceCatalogService service = new InterfaceCatalogService(trafficToolConfig, Map.of());
        InterfaceCatalogController controller = new InterfaceCatalogController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void catalog_listsConfiguredInterfaces() throws Exception {
        mockMvc.perform(get("/api/interfaces/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("pets"))
                .andExpect(jsonPath("$[0].name").value("Pets REST Interface"));
    }
}
