package com.example.monitor.api;

import com.example.monitor.autoreply.AutoReplySettingsService;
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

import static org.mockito.Mockito.verify;
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
class AutoReplyControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AutoReplySettingsService autoReplySettingsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AutoReplyController(autoReplySettingsService)).build();
    }

    @Test
    void getSettings_returnsGlobalAndInterfaceSettings() throws Exception {
        when(autoReplySettingsService.isGlobalEnabled()).thenReturn(true);
        when(autoReplySettingsService.allInterfaceSettings()).thenReturn(Map.of(
                "Fruit Interface", new AutoReplySettingsService.InterfaceAutoReplySettings(true, "localhost", 7001, "UDP")));

        mockMvc.perform(get("/api/autoreply/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalEnabled").value(true))
                .andExpect(jsonPath("$.interfaces['Fruit Interface'].enabled").value(true))
                .andExpect(jsonPath("$.interfaces['Fruit Interface'].host").value("localhost"))
                .andExpect(jsonPath("$.interfaces['Fruit Interface'].transport").value("UDP"));
    }

    @Test
    void updateGlobal_invokesServiceSetGlobalEnabledAndReturnsUpdatedSettings() throws Exception {
        when(autoReplySettingsService.isGlobalEnabled()).thenReturn(true);
        when(autoReplySettingsService.allInterfaceSettings()).thenReturn(Map.of());

        mockMvc.perform(post("/api/autoreply/global")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateAutoReplyGlobalRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.globalEnabled").value(true));

        verify(autoReplySettingsService).setGlobalEnabled(true);
    }

    @Test
    void updateInterface_invokesServiceUpdateInterfaceSettingsAndReturnsUpdatedSettings() throws Exception {
        when(autoReplySettingsService.isGlobalEnabled()).thenReturn(false);
        when(autoReplySettingsService.allInterfaceSettings()).thenReturn(Map.of());

        UpdateAutoReplyInterfaceRequest request = new UpdateAutoReplyInterfaceRequest("Ping Interface", true, "host", 7002, "TCP");

        mockMvc.perform(post("/api/autoreply/interface")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(autoReplySettingsService).updateInterfaceSettings("Ping Interface", true, "host", 7002, "TCP");
    }
}
