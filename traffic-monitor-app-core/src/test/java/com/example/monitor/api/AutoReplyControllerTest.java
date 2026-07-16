package com.example.monitor.api;

import com.example.monitor.autoreply.AutoReplySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoReplyController.class)
class AutoReplyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AutoReplySettingsService autoReplySettingsService;

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
