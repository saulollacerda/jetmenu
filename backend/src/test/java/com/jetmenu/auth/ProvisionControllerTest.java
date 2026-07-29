package com.jetmenu.auth;

import com.jetmenu.merchant.DuplicateMerchantException;
import com.jetmenu.merchant.MerchantResponse;
import com.jetmenu.merchant.MerchantStatus;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProvisionController.class)
@WithMockUser(username = ProvisionControllerTest.SUPABASE_UID)
@DisplayName("ProvisionController")
class ProvisionControllerTest {

    static final String SUPABASE_UID = "11111111-2222-3333-4444-555555555555";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProvisionService provisionService;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
    }

    private ProvisionRequest validRequest() {
        return ProvisionRequest.builder()
                .merchantName("Restaurante Novo")
                .cnpj("12345678000195")
                .email("novo@example.com")
                .phone("11999990000")
                .build();
    }

    @Test
    @DisplayName("deve retornar 200 com MerchantResponse ao provisionar")
    void shouldProvisionAndReturn200() throws Exception {
        MerchantResponse response = MerchantResponse.builder()
                .id(merchantId)
                .merchantName("Restaurante Novo")
                .email("novo@example.com")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        given(provisionService.provision(eq(SUPABASE_UID), any(ProvisionRequest.class))).willReturn(response);

        mockMvc.perform(post("/api/auth/provision")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(merchantId.toString()))
                .andExpect(jsonPath("$.email").value("novo@example.com"));
    }

    @Test
    @DisplayName("deve retornar 400 quando CNPJ é inválido")
    void shouldReturn400WhenCnpjInvalid() throws Exception {
        ProvisionRequest invalid = ProvisionRequest.builder()
                .merchantName("Restaurante Novo")
                .cnpj("123")
                .email("novo@example.com")
                .build();

        mockMvc.perform(post("/api/auth/provision")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("deve retornar 409 quando email/CNPJ já cadastrado")
    void shouldReturn409WhenDuplicate() throws Exception {
        given(provisionService.provision(eq(SUPABASE_UID), any(ProvisionRequest.class)))
                .willThrow(new DuplicateMerchantException("email"));

        mockMvc.perform(post("/api/auth/provision")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict());
    }
}
