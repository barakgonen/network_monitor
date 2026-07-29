package com.example.monitor.api;

import com.example.monitor.publishing.PeriodicPublisherService;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc setup (no Spring context): {@code @WebMvcTest} no longer exists as of
 * Spring Boot 4, so the controller is wired directly with a Mockito mock instead.
 */
@ExtendWith(MockitoExtension.class)
class PeriodicPublishControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PeriodicPublisherService periodicPublisherService;

    private MockMvc mockMvc;

    private static final PeriodicPublishStatus RUNNING_STATUS = new PeriodicPublishStatus(
            true, "Fruit Interface", "Orange", "localhost", 7001, 10, "SECOND", 100, 5, null);

    private static final PeriodicPublishStatus STOPPED_STATUS = new PeriodicPublishStatus(
            false, null, null, null, 0, 0, null, 0, 0, null);

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PeriodicPublishController(periodicPublisherService)).build();
    }

    @Test
    void start_delegatesToServiceAndReturnsStatus() throws Exception {
        PeriodicPublishRequest request = new PeriodicPublishRequest(
                new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of()), 10, "SECOND");
        when(periodicPublisherService.start(request)).thenReturn(RUNNING_STATUS);

        mockMvc.perform(post("/api/publish/udp/periodic/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(true))
                .andExpect(jsonPath("$.sentCount").value(5));
    }

    @Test
    void stop_delegatesToServiceAndReturnsStatus() throws Exception {
        when(periodicPublisherService.stop()).thenReturn(STOPPED_STATUS);

        mockMvc.perform(post("/api/publish/udp/periodic/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.running").value(false));
    }

    @Test
    void status_delegatesToServiceAndReturnsStatus() throws Exception {
        when(periodicPublisherService.status()).thenReturn(RUNNING_STATUS);

        mockMvc.perform(get("/api/publish/udp/periodic/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interfaceName").value("Fruit Interface"))
                .andExpect(jsonPath("$.sentCount").value(5));
    }
}
