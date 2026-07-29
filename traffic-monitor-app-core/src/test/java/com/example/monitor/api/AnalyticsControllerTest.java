package com.example.monitor.api;

import com.example.monitor.persistence.BreakdownCount;
import com.example.monitor.persistence.GroupByField;
import com.example.monitor.persistence.MessageArchiveRepository;
import com.example.monitor.persistence.TimeBucket;
import com.example.monitor.persistence.TimeBucketCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc setup (no Spring context): {@code @WebMvcTest} no longer exists as of
 * Spring Boot 4, so the controller is wired directly with a Mockito mock instead.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

    @Mock
    private MessageArchiveRepository messageArchiveRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(messageArchiveRepository)).build();
    }

    @Test
    void timeseries_withDefaultBucket_returnsHourlyPoints() throws Exception {
        when(messageArchiveRepository.countByTimeBucket(any(), any(), eq(TimeBucket.HOUR)))
                .thenReturn(List.of(new TimeBucketCount(Instant.parse("2026-01-01T10:00:00Z"), 5)));

        mockMvc.perform(get("/api/analytics/timeseries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("hour"))
                .andExpect(jsonPath("$.points[0].count").value(5));
    }

    @Test
    void timeseries_withExplicitMinuteBucket_delegatesCorrectly() throws Exception {
        when(messageArchiveRepository.countByTimeBucket(any(), any(), eq(TimeBucket.MINUTE)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/timeseries").param("bucket", "minute"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bucket").value("minute"));
    }

    @Test
    void timeseries_withInvalidBucket_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/timeseries").param("bucket", "fortnight"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void breakdown_withGroupByInterfaceName_returnsEntries() throws Exception {
        when(messageArchiveRepository.countByField(any(), any(), eq(GroupByField.INTERFACE_NAME)))
                .thenReturn(List.of(new BreakdownCount("Fruit Interface", 3)));

        mockMvc.perform(get("/api/analytics/breakdown").param("groupBy", "interfaceName"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("interfaceName"))
                .andExpect(jsonPath("$.entries[0].key").value("Fruit Interface"))
                .andExpect(jsonPath("$.entries[0].count").value(3));
    }

    @Test
    void breakdown_withGroupByMessageType_delegatesCorrectly() throws Exception {
        when(messageArchiveRepository.countByField(any(), any(), eq(GroupByField.MESSAGE_TYPE)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/analytics/breakdown").param("groupBy", "messageType"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupBy").value("messageType"));
    }

    @Test
    void breakdown_withInvalidGroupBy_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/breakdown").param("groupBy", "nonsense"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void breakdown_withMissingGroupBy_returns400() throws Exception {
        mockMvc.perform(get("/api/analytics/breakdown"))
                .andExpect(status().isBadRequest());
    }
}
