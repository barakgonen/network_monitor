package com.example.monitor.api;

import com.example.monitor.publishing.MonitorPayloadFactory;
import com.example.monitor.publishing.TcpMessagePublisher;
import com.example.monitor.publishing.UdpMessagePublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublishController.class)
class PublishControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MonitorPayloadFactory payloadFactory;

    @MockBean
    private UdpMessagePublisher udpMessagePublisher;

    @MockBean
    private TcpMessagePublisher tcpMessagePublisher;

    @Test
    void publishUdp_withValidRequest_returnsSuccessResponseAndInvokesPublisher() throws Exception {
        byte[] payload = {1, 2, 3};
        when(payloadFactory.create(eq("Fruit Interface"), eq("Orange"), any())).thenReturn(payload);

        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of("sourceFarm", "farm"));

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.bytesSent").value(3))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(udpMessagePublisher).send("localhost", 7001, payload);
    }

    @Test
    void publishUdp_withMissingInterfaceName_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest(null, "Orange", "localhost", 7001, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("interfaceName is required"));
    }

    @Test
    void publishUdp_withMissingMessageType_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest("Fruit Interface", null, "localhost", 7001, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("messageType is required"));
    }

    @Test
    void publishUdp_withBlankHost_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "  ", 7001, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("host is required"));
    }

    @Test
    void publishUdp_withInvalidPortZero_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 0, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid UDP port: 0"));
    }

    @Test
    void publishUdp_withInvalidPortOver65535_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 70000, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid UDP port: 70000"));
    }

    @Test
    void publishUdp_whenPayloadFactoryThrows_returnsSuccessFalseWithExceptionMessage() throws Exception {
        when(payloadFactory.create(anyString(), anyString(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported message"));

        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Unsupported message"));
    }

    @Test
    void publishUdp_withTcpTransport_invokesTcpPublisherNotUdpPublisher() throws Exception {
        byte[] payload = {1, 2, 3};
        when(payloadFactory.create(eq("Fruit Interface"), eq("Orange"), any())).thenReturn(payload);

        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, "TCP", Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(tcpMessagePublisher).send("localhost", 7001, payload);
        verify(udpMessagePublisher, never()).send(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void publishUdp_withNullTransport_defaultsToUdpPublisher() throws Exception {
        byte[] payload = {1, 2, 3};
        when(payloadFactory.create(eq("Fruit Interface"), eq("Orange"), any())).thenReturn(payload);

        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, null, Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(udpMessagePublisher).send("localhost", 7001, payload);
        verify(tcpMessagePublisher, never()).send(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void publishUdp_withInvalidTransport_returnsSuccessFalseWithValidationError() throws Exception {
        PublishRequest request = new PublishRequest("Fruit Interface", "Orange", "localhost", 7001, "CARRIER_PIGEON", Map.of());

        mockMvc.perform(post("/api/publish/udp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Invalid transport: CARRIER_PIGEON"));
    }
}
