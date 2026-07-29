package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodUserCodeResponse;
import com.jetmenu.integration.ifood.services.IfoodIntegrationSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;



import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodAuthController.class)
@WithMockUser
@DisplayName("IfoodAuthController")
class IfoodAuthControllerTest {

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
    @DisplayName("POST /api/integrations/ifood/auth/start retorna userCode, URLs e expiresIn")
    void start_shouldReturnUserCode() throws Exception {
        IfoodUserCodeResponse response = new IfoodUserCodeResponse();
        response.setUserCode("HJLX-LPSQ");
        response.setVerificationUrl("https://portal.ifood.com.br/apps/code");
        response.setVerificationUrlComplete("https://portal.ifood.com.br/apps/code?c=HJLX-LPSQ");
        response.setExpiresIn(600);
        response.setAuthorizationCodeVerifier("super-secret-verifier");
        given(tokenService.startAuthorization(merchantId)).willReturn(response);

        mockMvc.perform(post("/api/integrations/ifood/auth/start").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userCode").value("HJLX-LPSQ"))
                .andExpect(jsonPath("$.verificationUrl").value("https://portal.ifood.com.br/apps/code"))
                .andExpect(jsonPath("$.verificationUrlComplete")
                        .value("https://portal.ifood.com.br/apps/code?c=HJLX-LPSQ"))
                .andExpect(jsonPath("$.expiresIn").value(600));
    }

    @Test
    @DisplayName("POST /api/integrations/ifood/auth/start NÃO expõe o authorizationCodeVerifier")
    void start_shouldNotExposeAuthorizationCodeVerifier() throws Exception {
        IfoodUserCodeResponse response = new IfoodUserCodeResponse();
        response.setUserCode("HJLX-LPSQ");
        response.setAuthorizationCodeVerifier("super-secret-verifier");
        response.setExpiresIn(600);
        given(tokenService.startAuthorization(merchantId)).willReturn(response);

        mockMvc.perform(post("/api/integrations/ifood/auth/start").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationCodeVerifier").doesNotExist());
    }

    @Test
    @DisplayName("POST /connect retorna 409 quando não há autorização pendente (verifier perdido)")
    void connect_shouldReturn409WhenNoPendingAuthorization() throws Exception {
        willThrow(new IllegalStateException("No pending authorization for merchant " + merchantId))
                .given(tokenService).connect(any(), eq("auth-code-123"));
        String body = objectMapper.writeValueAsString(Map.of("authorizationCode", "auth-code-123"));

        mockMvc.perform(post("/api/integrations/ifood/auth/connect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("POST /connect retorna 400 quando o iFood rejeita o authorizationCode")
    void connect_shouldReturn400WhenIfoodRejectsCode() throws Exception {
        willThrow(HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8))
                .given(tokenService).connect(any(), eq("bad-code"));
        String body = objectMapper.writeValueAsString(Map.of("authorizationCode", "bad-code"));

        mockMvc.perform(post("/api/integrations/ifood/auth/connect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("POST /api/integrations/ifood/auth/connect retorna 200 após conectar")
    void connect_shouldReturn200() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("authorizationCode", "auth-code-123"));

        mockMvc.perform(post("/api/integrations/ifood/auth/connect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        verify(tokenService).connect(any(), eq("auth-code-123"));
    }

    @Test
    @DisplayName("DELETE /api/integrations/ifood/auth/revoke retorna 204")
    void revoke_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/integrations/ifood/auth/revoke").with(csrf()))
                .andExpect(status().isNoContent());

        verify(tokenService).revoke(any());
    }

    @Test
    @DisplayName("GET /api/integrations/ifood/auth/status retorna o checklist completo quando integrado")
    void status_shouldReturnFullChecklistWhenIntegrated() throws Exception {
        given(settingsService.getStatus(merchantId)).willReturn(
                new IfoodIntegrationStatus(true, LocalDateTime.of(2026, 7, 1, 10, 0), true));

        mockMvc.perform(get("/api/integrations/ifood/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.catalogImportedAt").value("2026-07-01T10:00:00"))
                .andExpect(jsonPath("$.orderSyncEnabled").value(true))
                .andExpect(jsonPath("$.connectionEnabled").value(true));
    }

    @Test
    @DisplayName("GET /api/integrations/ifood/auth/status retorna tudo desligado quando não integrado")
    void status_shouldReturnDisconnectedChecklistWhenNotIntegrated() throws Exception {
        given(settingsService.getStatus(merchantId)).willReturn(IfoodIntegrationStatus.disconnected());

        mockMvc.perform(get("/api/integrations/ifood/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(false))
                .andExpect(jsonPath("$.catalogImportedAt").value((Object) null))
                .andExpect(jsonPath("$.orderSyncEnabled").value(false));
    }

    @Test
    @DisplayName("POST /api/integrations/ifood/auth/connect retorna 400 sem authorizationCode")
    void connect_shouldReturn400WhenAuthorizationCodeMissing() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of());

        mockMvc.perform(post("/api/integrations/ifood/auth/connect").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
