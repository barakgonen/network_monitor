package com.example.monitor.api;

import com.example.monitor.rest.RestApiDefinition;
import com.example.monitor.rest.RestAutoReplySettingsService;
import com.example.monitor.rest.RestOperationDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc setup (no {@code @WebMvcTest} in Spring Boot 4, see {@code AutoReplyControllerTest}). */
class RestAutoReplyControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        RestOperationDefinition getOrder = new RestOperationDefinition(
                "orders", "getOrder", "GET", "/orders/{orderId}", List.of(), List.of(), List.of(),
                null, Map.of(), "Fetch an order", false);
        RestApiDefinition ordersApi = new RestApiDefinition("orders", List.of(getOrder));

        Map<String, RestApiDefinition> restApiDefinitions = Map.of("orders", ordersApi);
        RestAutoReplySettingsService autoReplySettingsService =
                new RestAutoReplySettingsService(restApiDefinitions, objectMapper);

        RestAutoReplyController controller = new RestAutoReplyController(restApiDefinitions, autoReplySettingsService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getSettings_returnsResolvedFallbackReply_whenNothingConfigured() throws Exception {
        mockMvc.perform(get("/api/rest/orders/autoreply"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.getOrder.statusCode").value(200))
                .andExpect(jsonPath("$.getOrder.body").value("{}"));
    }

    @Test
    void getSettings_withUnknownInterface_returns400() throws Exception {
        mockMvc.perform(get("/api/rest/unknown/autoreply"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_persistsConfiguredReply_andReturnsIt() throws Exception {
        mockMvc.perform(post("/api/rest/orders/autoreply/getOrder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateRestAutoReplyRequest(404, "{\"error\":\"not found\"}"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusCode").value(404))
                .andExpect(jsonPath("$.body").value("{\"error\":\"not found\"}"));

        // Subsequent GET reflects the now-configured value, not the fallback.
        mockMvc.perform(get("/api/rest/orders/autoreply"))
                .andExpect(jsonPath("$.getOrder.statusCode").value(404));
    }
}
