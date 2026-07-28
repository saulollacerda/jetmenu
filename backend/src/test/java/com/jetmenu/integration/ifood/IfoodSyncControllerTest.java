package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.services.IfoodIntegrationSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodSyncController.class)
@WithMockUser
@DisplayName("IfoodSyncController")
class IfoodSyncControllerTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private IfoodIntegrationSettingsService settingsService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("PUT /api/integrations/ifood/sync ativa a sincronia e retorna o status atualizado")
    void setSync_shouldEnableAndReturnUpdatedStatus() throws Exception {
        given(settingsService.setOrderSyncEnabled(merchantId, true)).willReturn(
                new IfoodIntegrationStatus(true, LocalDateTime.of(2026, 7, 1, 10, 0), true));
        String body = objectMapper.writeValueAsString(Map.of("enabled", true));

        mockMvc.perform(put("/api/integrations/ifood/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.orderSyncEnabled").value(true));
    }

    @Test
    @DisplayName("PUT /api/integrations/ifood/sync desativa a sincronia")
    void setSync_shouldDisable() throws Exception {
        given(settingsService.setOrderSyncEnabled(merchantId, false)).willReturn(
                new IfoodIntegrationStatus(true, null, false));
        String body = objectMapper.writeValueAsString(Map.of("enabled", false));

        mockMvc.perform(put("/api/integrations/ifood/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderSyncEnabled").value(false));
    }

    @Test
    @DisplayName("retorna 409 quando o merchant não está conectado ao iFood")
    void setSync_shouldReturn409WhenNotConnected() throws Exception {
        willThrow(new IllegalStateException("not connected"))
                .given(settingsService).setOrderSyncEnabled(merchantId, true);
        String body = objectMapper.writeValueAsString(Map.of("enabled", true));

        mockMvc.perform(put("/api/integrations/ifood/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("retorna 400 quando o body não traz o campo enabled")
    void setSync_shouldReturn400WhenEnabledMissing() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of());

        mockMvc.perform(put("/api/integrations/ifood/sync").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
