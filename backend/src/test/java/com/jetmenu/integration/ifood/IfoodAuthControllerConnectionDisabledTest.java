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

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = IfoodAuthController.class, properties = "ifood.connection-enabled=false")
@WithMockUser
@DisplayName("IfoodAuthController — connection disabled (homologation pending)")
class IfoodAuthControllerConnectionDisabledTest {

    @Autowired private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean private IfoodTokenService tokenService;
    @MockitoBean private IfoodIntegrationSettingsService settingsService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("POST /start retorna 503 e não inicia autorização")
    void start_shouldReturn503AndNotStartAuthorization() throws Exception {
        mockMvc.perform(post("/api/integrations/ifood/auth/start").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").exists());

        verify(tokenService, never()).startAuthorization(any());
    }

    @Test
    @DisplayName("POST /connect retorna 503 e não conecta")
    void connect_shouldReturn503AndNotConnect() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("authorizationCode", "auth-code-123"));

        mockMvc.perform(post("/api/integrations/ifood/auth/connect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").exists());

        verify(tokenService, never()).connect(any(), any());
    }

    @Test
    @DisplayName("GET /status continua acessível e expõe connectionEnabled=false")
    void status_shouldExposeConnectionDisabled() throws Exception {
        given(settingsService.getStatus(merchantId)).willReturn(IfoodIntegrationStatus.disconnected());

        mockMvc.perform(get("/api/integrations/ifood/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connectionEnabled").value(false))
                .andExpect(jsonPath("$.connected").value(false));
    }

    @Test
    @DisplayName("DELETE /revoke continua permitido (desconectar nunca é bloqueado)")
    void revoke_shouldStillBeAllowed() throws Exception {
        mockMvc.perform(delete("/api/integrations/ifood/auth/revoke").with(csrf()))
                .andExpect(status().isNoContent());

        verify(tokenService).revoke(any());
    }
}
