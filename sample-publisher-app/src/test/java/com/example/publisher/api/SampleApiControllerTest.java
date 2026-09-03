package com.example.publisher.api;

import com.example.publisher.sample.SampleStore;
import com.example.publisher.sample.SavedSample;
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

import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SampleApiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private SampleStore sampleStore;

    private MockMvc mockMvc;

    private static final SavedSample SAMPLE = new SavedSample(
            "sample-1", new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of()), 5, "SECOND");

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleApiController(sampleStore)).build();
    }

    @Test
    void list_returnsSamplesFromStore() throws Exception {
        when(sampleStore.list()).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/samples"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("sample-1"));
    }

    @Test
    void save_delegatesToStoreAndReturnsSavedSample() throws Exception {
        when(sampleStore.save(SAMPLE)).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/samples")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(SAMPLE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("sample-1"))
                .andExpect(jsonPath("$.eventsPerTimeUnit").value(5));
    }

    @Test
    void delete_delegatesToStore() throws Exception {
        mockMvc.perform(delete("/api/samples/sample-1"))
                .andExpect(status().isNoContent());

        verify(sampleStore).delete("sample-1");
    }

    @Test
    void export_setsContentDispositionHeader() throws Exception {
        when(sampleStore.list()).thenReturn(List.of(SAMPLE));

        mockMvc.perform(get("/api/samples/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"publisher-samples.json\""))
                .andExpect(jsonPath("$[0].name").value("sample-1"));
    }

    @Test
    void importAll_delegatesToStoreAndReturnsUpdatedList() throws Exception {
        when(sampleStore.importAll(List.of(SAMPLE))).thenReturn(List.of(SAMPLE));

        mockMvc.perform(post("/api/samples/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(List.of(SAMPLE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("sample-1"));
    }
}
