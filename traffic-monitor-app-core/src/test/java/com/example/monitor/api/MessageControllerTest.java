package com.example.monitor.api;

import com.example.monitor.model.ObservedMessage;
import com.example.monitor.store.RecentMessageStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessageController.class)
class MessageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RecentMessageStore recentMessageStore;

    @Test
    void getRecentMessages_returnsStoreContentsAsJson() throws Exception {
        ObservedMessage message = new ObservedMessage(
                "id-1", Instant.parse("2026-01-01T00:00:00Z"), "UDP", "127.0.0.1:1234", 5001,
                "Fruit Interface", "Orange", Map.of(), Map.of("sourceFarm", "farm"), 10, "text", "base64", null);

        when(recentMessageStore.recent()).thenReturn(List.of(message));

        mockMvc.perform(get("/api/messages/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("id-1"))
                .andExpect(jsonPath("$[0].interfaceName").value("Fruit Interface"))
                .andExpect(jsonPath("$[0].messageType").value("Orange"));
    }

    @Test
    void getRecentMessages_whenStoreEmpty_returnsEmptyArray() throws Exception {
        when(recentMessageStore.recent()).thenReturn(List.of());

        mockMvc.perform(get("/api/messages/recent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
