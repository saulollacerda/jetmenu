package com.jetmenu.auth;

import com.jetmenu.merchant.MerchantResponse;
import com.jetmenu.merchant.MerchantStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalAuthController.class)
@WithMockUser
@DisplayName("LocalAuthController")
class LocalAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private LocalAuthService localAuthService;

    private DevAuthResponse response() {
        MerchantResponse merchant = MerchantResponse.builder()
                .id(UUID.randomUUID())
                .merchantName("Restaurante Dev")
                .email("dev@example.com")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        return DevAuthResponse.builder().accessToken("jwt-token").merchant(merchant).build();
    }

    @Test
    @DisplayName("POST /api/auth/dev-register retorna 200 com token")
    void register_shouldReturnToken() throws Exception {
        given(localAuthService.register(any(DevRegisterRequest.class))).willReturn(response());

        DevRegisterRequest request = DevRegisterRequest.builder()
                .merchantName("Restaurante Dev")
                .cnpj("12345678000195")
                .email("dev@example.com")
                .phone("11999990000")
                .password("senha123")
                .build();

        mockMvc.perform(post("/api/auth/dev-register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.merchant.email").value("dev@example.com"));
    }

    @Test
    @DisplayName("POST /api/auth/dev-register com dados inválidos retorna 400")
    void register_shouldRejectInvalidPayload() throws Exception {
        DevRegisterRequest invalid = DevRegisterRequest.builder()
                .merchantName("")
                .cnpj("")
                .email("nao-email")
                .password("123")
                .build();

        mockMvc.perform(post("/api/auth/dev-register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/auth/dev-login retorna 200 com token")
    void login_shouldReturnToken() throws Exception {
        given(localAuthService.login(any(DevLoginRequest.class))).willReturn(response());

        DevLoginRequest request = new DevLoginRequest("dev@example.com", "senha123");

        mockMvc.perform(post("/api/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt-token"));
    }

    @Test
    @DisplayName("POST /api/auth/dev-login com credenciais inválidas retorna 401")
    void login_shouldReturnUnauthorizedOnBadCredentials() throws Exception {
        willThrow(new InvalidCredentialsException())
                .given(localAuthService).login(any(DevLoginRequest.class));

        DevLoginRequest request = new DevLoginRequest("dev@example.com", "errada");

        mockMvc.perform(post("/api/auth/dev-login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
