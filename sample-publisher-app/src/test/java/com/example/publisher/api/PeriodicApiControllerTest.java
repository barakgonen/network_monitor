package com.example.publisher.api;

import com.example.publisher.periodic.PeriodicJobDto;
import com.example.publisher.periodic.PeriodicSchedulerService;
import com.example.publisher.periodic.PeriodicStartRequest;
import com.example.publisher.send.SendRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PeriodicApiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PeriodicSchedulerService scheduler;

    private MockMvc mockMvc;

    private static final PeriodicJobDto JOB = new PeriodicJobDto(
            "job-1", "fruit", "Orange", "localhost", 5001, "UDP",
            5, "SECOND", 200L, true, 3L, null, Instant.parse("2026-08-31T00:00:00Z"));

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PeriodicApiController(scheduler)).build();
    }

    @Test
    void start_delegatesToSchedulerAndReturnsJob() throws Exception {
        PeriodicStartRequest request = new PeriodicStartRequest(
                new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of()), 5, "SECOND");
        when(scheduler.start(request)).thenReturn(JOB);

        mockMvc.perform(post("/api/periodic/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"))
                .andExpect(jsonPath("$.running").value(true));
    }

    @Test
    void stop_withKnownJobId_returnsStoppedJob() throws Exception {
        when(scheduler.stop("job-1")).thenReturn(Optional.of(JOB));

        mockMvc.perform(post("/api/periodic/job-1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-1"));
    }

    @Test
    void stop_withUnknownJobId_returns404() throws Exception {
        when(scheduler.stop("missing")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/periodic/missing/stop"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsAllJobs() throws Exception {
        when(scheduler.list()).thenReturn(List.of(JOB));

        mockMvc.perform(get("/api/periodic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-1"));
    }

    @Test
    void status_withUnknownJobId_returns404() throws Exception {
        when(scheduler.status("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/periodic/missing"))
                .andExpect(status().isNotFound());
    }
}
