package com.example.publisher.api;

import com.example.publisher.dto.FieldDto;
import com.example.publisher.metadata.FieldMetadataService;
import com.example.publisher.metadata.PublishableInterfaceDto;
import com.example.publisher.metadata.PublishableInterfaceService;
import com.example.publisher.metadata.PublishableMessageDto;
import com.example.publisher.send.SendOrchestrationService;
import com.example.publisher.send.SendRequest;
import com.example.publisher.send.SendResult;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PublisherApiControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PublishableInterfaceService interfaceService;
    @Mock
    private FieldMetadataService fieldMetadataService;
    @Mock
    private SendOrchestrationService sendOrchestrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PublisherApiController(interfaceService, fieldMetadataService, sendOrchestrationService)).build();
    }

    @Test
    void interfaces_returnsListFromService() throws Exception {
        when(interfaceService.list()).thenReturn(List.of(
                new PublishableInterfaceDto("fruit", "Fruit Interface", "UDP", 5001,
                        List.of(new PublishableMessageDto("Orange", "Orange")))));

        mockMvc.perform(get("/api/interfaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("fruit"))
                .andExpect(jsonPath("$[0].messages[0].id").value("Orange"));
    }

    @Test
    void fields_delegatesToFieldMetadataService() throws Exception {
        when(fieldMetadataService.describe("fruit", "Orange"))
                .thenReturn(List.of(new FieldDto("sourceFarm", "string")));

        mockMvc.perform(get("/api/fields").param("interfaceKey", "fruit").param("messageId", "Orange"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("sourceFarm"))
                .andExpect(jsonPath("$[0].type").value("string"));
    }

    @Test
    void send_delegatesToSendOrchestrationService() throws Exception {
        SendRequest request = new SendRequest("fruit", "Orange", "localhost", 5001, "UDP", Map.of("sourceFarm", "x"));
        when(sendOrchestrationService.send(eq(request))).thenReturn(SendResult.sent(42, List.of("localhost:5001")));

        mockMvc.perform(post("/api/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.bytesSent").value(42));
    }
}
