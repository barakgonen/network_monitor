package com.example.monitor.api;

import com.example.monitor.ingestion.rest.RestIngestionRunner;
import com.example.monitor.ingestion.tcp.TcpIngestionRunner;
import com.example.monitor.ingestion.udp.UdpIngestionRunner;
import com.example.monitor.interfaces.InterfaceControlService;
import com.example.monitor.interfaces.InterfaceRuntimeRegistry;
import com.example.trafficconfig.InterfaceConfig;
import com.example.trafficconfig.TrafficToolConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc setup (no {@code @WebMvcTest} in Spring Boot 4, see {@code AutoReplyControllerTest}). */
class InterfaceControlControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        InterfaceConfig radaConfig = new InterfaceConfig();
        radaConfig.setKey("rada");
        radaConfig.setName("Rada Interface");
        radaConfig.setPort(5050);
        radaConfig.setProtocol("UDP");

        TrafficToolConfig trafficToolConfig = new TrafficToolConfig();
        trafficToolConfig.setInterfaces(List.of(radaConfig));

        InterfaceRuntimeRegistry runtimeRegistry = new InterfaceRuntimeRegistry(trafficToolConfig);
        InterfaceControlService service = new InterfaceControlService(
                runtimeRegistry,
                Mockito.mock(UdpIngestionRunner.class),
                Mockito.mock(TcpIngestionRunner.class),
                Mockito.mock(RestIngestionRunner.class));

        InterfaceControlController controller = new InterfaceControlController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void list_returnsConfiguredInterfaces() throws Exception {
        mockMvc.perform(get("/api/interfaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].key").value("rada"));
    }

    @Test
    void start_withUnknownInterfaceKey_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/interfaces/unknown/start"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void stop_withUnknownInterfaceKey_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/interfaces/unknown/stop"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void configure_withUnknownInterfaceKey_returns400NotServerError() throws Exception {
        mockMvc.perform(post("/api/interfaces/unknown/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new com.example.monitor.interfaces.InterfaceConfigureRequest(5050, "UDP", "SERVER", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void start_withKnownInterfaceKey_returnsUpdatedStatuses() throws Exception {
        mockMvc.perform(post("/api/interfaces/rada/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("rada"));
    }
}
