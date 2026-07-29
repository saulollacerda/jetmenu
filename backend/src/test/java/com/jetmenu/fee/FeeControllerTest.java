package com.jetmenu.fee;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FeeController.class)
@WithMockUser
@DisplayName("FeeController")
class FeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FeeService feeService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID feeId;
    private UUID merchantId;
    private FeeResponse feeResponse;

    @BeforeEach
    void setUp() {
        feeId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        feeResponse = FeeResponse.builder()
                .id(feeId)
                .name("Crédito")
                .feeRate(new BigDecimal("2.5000"))
                .build();
    }

    private FeeRequest buildValidRequest() {
        return FeeRequest.builder()
                .name("Crédito")
                .feeRate(new BigDecimal("2.5000"))
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/fees
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/fees")
    class Create {

        @Test
        @DisplayName("deve retornar 201 com FeeResponse ao criar com dados válidos")
        void shouldReturn201WithResponse() throws Exception {
            given(feeService.create(any(), any(FeeRequest.class))).willReturn(feeResponse);

            mockMvc.perform(post("/api/fees")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(feeId.toString()))
                    .andExpect(jsonPath("$.name").value("Crédito"))
                    .andExpect(jsonPath("$.feeRate").value(2.5));
        }

        @Test
        @DisplayName("deve retornar 400 quando nome está ausente")
        void shouldReturn400WhenNameMissing() throws Exception {
            mockMvc.perform(post("/api/fees")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    FeeRequest.builder().feeRate(new BigDecimal("2.5")).build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando taxa está ausente")
        void shouldReturn400WhenFeeRateMissing() throws Exception {
            mockMvc.perform(post("/api/fees")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    FeeRequest.builder().name("Crédito").build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando nome já está em uso")
        void shouldReturn409WhenNameAlreadyInUse() throws Exception {
            given(feeService.create(any(), any(FeeRequest.class)))
                    .willThrow(new DuplicateFeeException("nome"));

            mockMvc.perform(post("/api/fees")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/fees/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/fees/{id}")
    class GetById {

        @Test
        @DisplayName("deve retornar 200 com FeeResponse quando existe")
        void shouldReturn200WhenExists() throws Exception {
            given(feeService.findById(any(), eq(feeId))).willReturn(feeResponse);

            mockMvc.perform(get("/api/fees/{id}", feeId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(feeId.toString()))
                    .andExpect(jsonPath("$.name").value("Crédito"));
        }

        @Test
        @DisplayName("deve retornar 404 quando não encontrada")
        void shouldReturn404WhenNotFound() throws Exception {
            given(feeService.findById(any(), eq(feeId)))
                    .willThrow(new FeeNotFoundException(feeId));

            mockMvc.perform(get("/api/fees/{id}", feeId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/fees
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/fees")
    class GetAll {

        @Test
        @DisplayName("deve retornar 200 com página de taxas")
        void shouldReturn200WithPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(feeService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(feeResponse), pageable, 1));

            mockMvc.perform(get("/api/fees"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(feeId.toString()))
                    .andExpect(jsonPath("$.content[0].name").value("Crédito"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(feeService.findAll(any(), eq("cred"), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(feeResponse), pageable, 1));

            mockMvc.perform(get("/api/fees").param("search", "cred"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].name").value("Crédito"));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/fees/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/fees/{id}")
    class Update {

        @Test
        @DisplayName("deve retornar 200 com FeeResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(feeService.update(any(), eq(feeId), any(FeeRequest.class)))
                    .willReturn(feeResponse);

            mockMvc.perform(put("/api/fees/{id}", feeId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(feeId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando não encontrada para atualização")
        void shouldReturn404WhenNotFound() throws Exception {
            given(feeService.update(any(), eq(feeId), any(FeeRequest.class)))
                    .willThrow(new FeeNotFoundException(feeId));

            mockMvc.perform(put("/api/fees/{id}", feeId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando dados inválidos na atualização")
        void shouldReturn400WhenInvalidDataForUpdate() throws Exception {
            mockMvc.perform(put("/api/fees/{id}", feeId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(FeeRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/fees/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/fees/{id}")
    class Delete {

        @Test
        @DisplayName("deve retornar 204 ao deletar taxa existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(feeService).delete(any(), eq(feeId));

            mockMvc.perform(delete("/api/fees/{id}", feeId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar taxa inexistente")
        void shouldReturn404WhenNotFound() throws Exception {
            willThrow(new FeeNotFoundException(feeId))
                    .given(feeService).delete(any(), eq(feeId));

            mockMvc.perform(delete("/api/fees/{id}", feeId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}
