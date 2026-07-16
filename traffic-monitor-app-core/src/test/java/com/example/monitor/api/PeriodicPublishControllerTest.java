package com.example.monitor.api;

import com.example.monitor.publishing.PeriodicPublisherService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PeriodicPublishController.class)
class PeriodicPublishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PeriodicPublisherService periodicPublisherService;

    private static final PeriodicPublishStatus RUNNING_STATUS = new PeriodicPublishStatus(
            true, "Fruit Interface", "Orange", "localhost", 7001, 10, "SECOND", 100, 5, null);

    private static final PeriodicPublishStatus STOPPED_STATUS = new PeriodicPublishStatus(
            false, null, null, null, 0, 0, null, 0, 0, null);

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
