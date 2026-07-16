package com.example.monitor.api;

import com.example.monitor.model.ObservedMessage;
import com.example.monitor.persistence.HistoryPage;
import com.example.monitor.persistence.HistoryQuery;
import com.example.monitor.persistence.MessageArchiveRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HistoryController.class)
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MessageArchiveRepository messageArchiveRepository;

    private static ObservedMessage message() {
        return new ObservedMessage(
                "id-1", Instant.parse("2026-01-01T00:00:00Z"), "UDP", "127.0.0.1:1234", 5001,
                "Fruit Interface", "Orange", Map.of(), Map.of("sourceFarm", "farm"), 10, "text", "base64", null);
    }

    @Test
    void history_withNoParams_usesDefaultsAndReturnsPage() throws Exception {
        when(messageArchiveRepository.findHistory(any())).thenReturn(new HistoryPage(List.of(message()), 1));

        mockMvc.perform(get("/api/messages/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.offset").value(0))
                .andExpect(jsonPath("$.items[0].id").value("id-1"));

        ArgumentCaptor<HistoryQuery> captor = ArgumentCaptor.forClass(HistoryQuery.class);
        verify(messageArchiveRepository).findHistory(captor.capture());
        assertThat(captor.getValue().limit()).isEqualTo(50);
        assertThat(captor.getValue().offset()).isEqualTo(0);
        assertThat(captor.getValue().parseErrorOnly()).isFalse();
    }

    @Test
    void history_passesThroughFilterParams() throws Exception {
        when(messageArchiveRepository.findHistory(any())).thenReturn(new HistoryPage(List.of(), 0));

        mockMvc.perform(get("/api/messages/history")
                        .param("interfaceName", "Fruit Interface")
                        .param("messageType", "Orange")
                        .param("parseErrorOnly", "true")
                        .param("from", "2026-01-01T00:00:00Z")
                        .param("to", "2026-01-02T00:00:00Z")
                        .param("limit", "10")
                        .param("offset", "20"))
                .andExpect(status().isOk());

        ArgumentCaptor<HistoryQuery> captor = ArgumentCaptor.forClass(HistoryQuery.class);
        verify(messageArchiveRepository).findHistory(captor.capture());
        HistoryQuery query = captor.getValue();
        assertThat(query.interfaceName()).isEqualTo("Fruit Interface");
        assertThat(query.messageType()).isEqualTo("Orange");
        assertThat(query.parseErrorOnly()).isTrue();
        assertThat(query.from()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(query.to()).isEqualTo(Instant.parse("2026-01-02T00:00:00Z"));
        assertThat(query.limit()).isEqualTo(10);
        assertThat(query.offset()).isEqualTo(20);
    }

    @Test
    void history_withLimitAboveMax_isCappedAtServerSideMax() throws Exception {
        when(messageArchiveRepository.findHistory(any())).thenReturn(new HistoryPage(List.of(), 0));

        mockMvc.perform(get("/api/messages/history").param("limit", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit").value(500));
    }

    @Test
    void history_withInvalidFromInstant_returns400() throws Exception {
        mockMvc.perform(get("/api/messages/history").param("from", "not-an-instant"))
                .andExpect(status().isBadRequest());
    }
}
