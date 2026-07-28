package com.jetmenu.merchant;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MerchantController.class)
@WithMockUser
@DisplayName("MerchantController")
class MerchantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MerchantService merchantService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID merchantId;
    private MerchantResponse merchantResponse;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        merchantResponse = MerchantResponse.builder()
                .id(merchantId)
                .merchantName("Restaurante Teste")
                .cnpj("12345678000195")
                .email("teste@email.com")
                .phone("11999999999")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private MerchantRequest buildValidRequest() {
        return MerchantRequest.builder()
                .merchantName("Restaurante Teste")
                .cnpj("12345678000195")
                .email("teste@email.com")
                .password("senha123")
                .confirmPassword("senha123")
                .phone("11999999999")
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/merchants
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/merchants")
    class CreateUser {

        @Test
        @DisplayName("deve retornar 201 com MerchantResponse ao criar usuário válido")
        void shouldReturn201WithMerchantResponse() throws Exception {
            given(merchantService.create(any(MerchantRequest.class))).willReturn(merchantResponse);

            mockMvc.perform(post("/api/merchants")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(merchantId.toString()))
                    .andExpect(jsonPath("$.email").value("teste@email.com"))
                    .andExpect(jsonPath("$.merchantName").value("Restaurante Teste"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/api/merchants")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(MerchantRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando email já está em uso")
        void shouldReturn409WhenEmailAlreadyInUse() throws Exception {
            given(merchantService.create(any(MerchantRequest.class)))
                    .willThrow(new DuplicateMerchantException("email"));

            mockMvc.perform(post("/api/merchants")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("deve retornar 409 quando CNPJ já está em uso")
        void shouldReturn409WhenCnpjAlreadyInUse() throws Exception {
            given(merchantService.create(any(MerchantRequest.class)))
                    .willThrow(new DuplicateMerchantException("CNPJ"));

            mockMvc.perform(post("/api/merchants")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/merchants/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/merchants/{id}")
    class GetUserById {

        @Test
        @DisplayName("deve retornar 200 com MerchantResponse quando usuário existe")
        void shouldReturn200WhenUserExists() throws Exception {
            given(merchantService.findById(any(), eq(merchantId))).willReturn(merchantResponse);

            mockMvc.perform(get("/api/merchants/{id}", merchantId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(merchantId.toString()))
                    .andExpect(jsonPath("$.email").value("teste@email.com"));
        }

        @Test
        @DisplayName("deve retornar 404 quando usuário não encontrado")
        void shouldReturn404WhenUserNotFound() throws Exception {
            given(merchantService.findById(any(), eq(merchantId))).willThrow(new MerchantNotFoundException(merchantId));

            mockMvc.perform(get("/api/merchants/{id}", merchantId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/merchants/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/merchants/{id}")
    class UpdateUser {

        @Test
        @DisplayName("deve retornar 200 com MerchantResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(merchantService.update(any(), eq(merchantId), any(MerchantRequest.class))).willReturn(merchantResponse);

            mockMvc.perform(put("/api/merchants/{id}", merchantId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(merchantId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando usuário não encontrado para atualização")
        void shouldReturn404WhenUserNotFoundForUpdate() throws Exception {
            given(merchantService.update(any(), eq(merchantId), any(MerchantRequest.class)))
                    .willThrow(new MerchantNotFoundException(merchantId));

            mockMvc.perform(put("/api/merchants/{id}", merchantId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(put("/api/merchants/{id}", merchantId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(MerchantRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/merchants/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/merchants/{id}")
    class DeleteUser {

        @Test
        @DisplayName("deve retornar 204 ao deletar usuário existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(merchantService).delete(any(), eq(merchantId));

            mockMvc.perform(delete("/api/merchants/{id}", merchantId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar usuário inexistente")
        void shouldReturn404WhenUserNotFoundForDelete() throws Exception {
            willThrow(new MerchantNotFoundException(merchantId)).given(merchantService).delete(any(), eq(merchantId));

            mockMvc.perform(delete("/api/merchants/{id}", merchantId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/merchants/me/anota-ai-key
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/merchants/me/anota-ai-key")
    class UpdateAnotaAIKey {

        @Test
        @DisplayName("deve retornar 200 com MerchantResponse contendo a nova chave")
        void shouldReturn200WithUpdatedUser() throws Exception {
            MerchantResponse withKey = MerchantResponse.builder()
                    .id(merchantId)
                    .merchantName(merchantResponse.getMerchantName())
                    .cnpj(merchantResponse.getCnpj())
                    .email(merchantResponse.getEmail())
                    .anotaAiApiKey("new-key")
                    .build();
            given(merchantService.updateAnotaAIKey(any(), any(AnotaAIKeyRequest.class))).willReturn(withKey);

            mockMvc.perform(put("/api/merchants/me/anota-ai-key")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new AnotaAIKeyRequest("new-key"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.anotaAiApiKey").value("new-key"));
        }
    }
}
