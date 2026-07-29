package com.jetmenu.ingredient;

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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IngredientController.class)
@WithMockUser
@DisplayName("IngredientController")
class IngredientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID ingredientId;
    private UUID merchantId;
    private IngredientResponse ingredientResponse;

    @BeforeEach
    void setUp() {
        ingredientId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        ingredientResponse = IngredientResponse.builder()
                .id(ingredientId)
                .name("Farinha de Trigo")
                .unit("kg")
                .costPerUnit(new BigDecimal("4.50"))
                .defaultQuantity(new BigDecimal("0.20"))
                .status(IngredientStatus.ACTIVE)
                .build();
    }

    private IngredientRequest buildValidRequest() {
        return IngredientRequest.builder()
                .name("Farinha de Trigo")
                .unit("kg")
                .costPerUnit(new BigDecimal("4.50"))
                .defaultQuantity(new BigDecimal("0.20"))
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/ingredients
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/ingredients")
    class CreateIngredient {

        @Test
        @DisplayName("deve retornar 201 com IngredientResponse ao criar ingrediente válido")
        void shouldReturn201WithIngredientResponse() throws Exception {
            given(ingredientService.create(any(), any(IngredientRequest.class))).willReturn(ingredientResponse);

            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                    .andExpect(jsonPath("$.name").value("Farinha de Trigo"))
                    .andExpect(jsonPath("$.unit").value("kg"))
                    .andExpect(jsonPath("$.costPerUnit").value(4.50))
                    .andExpect(jsonPath("$.defaultQuantity").value(0.20));
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(IngredientRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando nome já está em uso")
        void shouldReturn409WhenNameAlreadyInUse() throws Exception {
            given(ingredientService.create(any(), any(IngredientRequest.class)))
                    .willThrow(new DuplicateIngredientException("nome"));

            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("deve aceitar costPerUnit com 4 casas decimais")
        void shouldAcceptFourDecimalPlacesInCostPerUnit() throws Exception {
            IngredientResponse fineGrainedResponse = IngredientResponse.builder()
                    .id(ingredientId)
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.0035"))
                    .status(IngredientStatus.ACTIVE)
                    .build();
            given(ingredientService.create(any(), any(IngredientRequest.class))).willReturn(fineGrainedResponse);

            IngredientRequest request = IngredientRequest.builder()
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.0035"))
                    .build();

            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.costPerUnit").value(0.0035));
        }

        @Test
        @DisplayName("deve aceitar costPerUnit zero")
        void shouldAcceptCostPerUnitZero() throws Exception {
            IngredientResponse zeroResponse = IngredientResponse.builder()
                    .id(ingredientId)
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(BigDecimal.ZERO)
                    .status(IngredientStatus.ACTIVE)
                    .build();
            given(ingredientService.create(any(), any(IngredientRequest.class))).willReturn(zeroResponse);

            IngredientRequest request = IngredientRequest.builder()
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(BigDecimal.ZERO)
                    .build();

            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.costPerUnit").value(0));
        }

        @Test
        @DisplayName("deve retornar 400 quando costPerUnit tem mais de 4 casas decimais")
        void shouldReturn400WhenCostPerUnitHasMoreThanFourDecimals() throws Exception {
            IngredientRequest request = IngredientRequest.builder()
                    .name("Açúcar refinado")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.00001"))
                    .build();

            mockMvc.perform(post("/api/ingredients")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/ingredients/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/ingredients/{id}")
    class GetIngredientById {

        @Test
        @DisplayName("deve retornar 200 com IngredientResponse quando ingrediente existe")
        void shouldReturn200WhenIngredientExists() throws Exception {
            given(ingredientService.findById(any(), eq(ingredientId))).willReturn(ingredientResponse);

            mockMvc.perform(get("/api/ingredients/{id}", ingredientId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                    .andExpect(jsonPath("$.name").value("Farinha de Trigo"))
                    .andExpect(jsonPath("$.defaultQuantity").value(0.20));
        }

        @Test
        @DisplayName("deve retornar 404 quando ingrediente não encontrado")
        void shouldReturn404WhenIngredientNotFound() throws Exception {
            given(ingredientService.findById(any(), eq(ingredientId)))
                    .willThrow(new IngredientNotFoundException(ingredientId));

            mockMvc.perform(get("/api/ingredients/{id}", ingredientId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/ingredients
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/ingredients")
    class GetAllIngredients {

        @Test
        @DisplayName("deve retornar 200 com página de ingredientes")
        void shouldReturn200WithIngredientPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(ingredientResponse), pageable, 1));

            mockMvc.perform(get("/api/ingredients"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(ingredientId.toString()))
                    .andExpect(jsonPath("$.content[0].defaultQuantity").value(0.20))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientService.findAll(any(), eq("bac"), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(ingredientResponse), pageable, 1));

            mockMvc.perform(get("/api/ingredients").param("search", "bac"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(ingredientId.toString()));
        }

        @Test
        @DisplayName("deve ordenar por position (ascendente) por padrão quando nenhum sort é informado")
        void shouldDefaultSortByPosition() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientService.findAll(any(), eq(""), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(ingredientResponse), pageable, 1));

            mockMvc.perform(get("/api/ingredients"))
                    .andExpect(status().isOk());

            org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> captor =
                    org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            then(ingredientService).should().findAll(any(), eq(""), captor.capture());
            org.springframework.data.domain.Sort.Order order =
                    captor.getValue().getSort().getOrderFor("position");
            org.assertj.core.api.Assertions.assertThat(order).isNotNull();
            org.assertj.core.api.Assertions.assertThat(order.getDirection())
                    .isEqualTo(org.springframework.data.domain.Sort.Direction.ASC);
        }
    }

    // -------------------------------------------------------------------------
    // PATCH /api/ingredients/{id}/position
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PATCH /api/ingredients/{id}/position")
    class ReorderIngredient {

        @Test
        @DisplayName("deve retornar 204 e repassar a nova posição ao service")
        void shouldReturn204AndReorder() throws Exception {
            willDoNothing().given(ingredientService).reorder(any(), eq(ingredientId), eq(5));

            mockMvc.perform(patch("/api/ingredients/{id}/position", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    IngredientPositionRequest.builder().position(5).build())))
                    .andExpect(status().isNoContent());

            then(ingredientService).should().reorder(any(), eq(ingredientId), eq(5));
        }

        @Test
        @DisplayName("deve retornar 400 quando position é nula")
        void shouldReturn400WhenPositionMissing() throws Exception {
            mockMvc.perform(patch("/api/ingredients/{id}/position", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    IngredientPositionRequest.builder().build())))
                    .andExpect(status().isBadRequest());

            then(ingredientService).should(never()).reorder(any(), any(), anyInt());
        }

        @Test
        @DisplayName("deve retornar 400 quando position é negativa")
        void shouldReturn400WhenPositionNegative() throws Exception {
            mockMvc.perform(patch("/api/ingredients/{id}/position", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    IngredientPositionRequest.builder().position(-1).build())))
                    .andExpect(status().isBadRequest());

            then(ingredientService).should(never()).reorder(any(), any(), anyInt());
        }

        @Test
        @DisplayName("deve retornar 404 quando ingrediente não existe")
        void shouldReturn404WhenIngredientNotFound() throws Exception {
            willThrow(new IngredientNotFoundException(ingredientId))
                    .given(ingredientService).reorder(any(), eq(ingredientId), eq(3));

            mockMvc.perform(patch("/api/ingredients/{id}/position", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    IngredientPositionRequest.builder().position(3).build())))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/ingredients/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/ingredients/{id}")
    class UpdateIngredient {

        @Test
        @DisplayName("deve retornar 200 com IngredientResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(ingredientService.update(any(), eq(ingredientId), any(IngredientRequest.class)))
                    .willReturn(ingredientResponse);

            mockMvc.perform(put("/api/ingredients/{id}", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ingredientId.toString()))
                    .andExpect(jsonPath("$.defaultQuantity").value(0.20));
        }

        @Test
        @DisplayName("deve retornar 404 quando ingrediente não encontrado para atualização")
        void shouldReturn404WhenIngredientNotFoundForUpdate() throws Exception {
            given(ingredientService.update(any(), eq(ingredientId), any(IngredientRequest.class)))
                    .willThrow(new IngredientNotFoundException(ingredientId));

            mockMvc.perform(put("/api/ingredients/{id}", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(put("/api/ingredients/{id}", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(IngredientRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/ingredients/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/ingredients/{id}")
    class DeleteIngredient {

        @Test
        @DisplayName("deve retornar 204 ao deletar ingrediente existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(ingredientService).delete(any(), eq(ingredientId));

            mockMvc.perform(delete("/api/ingredients/{id}", ingredientId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar ingrediente inexistente")
        void shouldReturn404WhenIngredientNotFoundForDelete() throws Exception {
            willThrow(new IngredientNotFoundException(ingredientId))
                    .given(ingredientService).delete(any(), eq(ingredientId));

            mockMvc.perform(delete("/api/ingredients/{id}", ingredientId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/ingredients/{id}/cost
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/ingredients/{id}/cost")
    class UpdateIngredientCost {

        private IngredientCostRequest buildValidCostRequest() {
            return IngredientCostRequest.builder()
                    .costPerUnit(new BigDecimal("0.08"))
                    .defaultQuantity(new BigDecimal("1"))
                    .unit("g")
                    .build();
        }

        @Test
        @DisplayName("deve retornar 200 com ingrediente atualizado")
        void shouldReturn200WithUpdatedIngredient() throws Exception {
            given(ingredientService.updateCost(any(), eq(ingredientId), any(IngredientCostRequest.class)))
                    .willReturn(ingredientResponse);

            mockMvc.perform(put("/api/ingredients/{id}/cost", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidCostRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(ingredientId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando ingrediente não existe")
        void shouldReturn404WhenIngredientNotFound() throws Exception {
            given(ingredientService.updateCost(any(), eq(ingredientId), any(IngredientCostRequest.class)))
                    .willThrow(new IngredientNotFoundException(ingredientId));

            mockMvc.perform(put("/api/ingredients/{id}/cost", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidCostRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando costPerUnit é negativo")
        void shouldReturn400WhenCostPerUnitIsNegative() throws Exception {
            IngredientCostRequest invalid = IngredientCostRequest.builder()
                    .costPerUnit(new BigDecimal("-1"))
                    .build();

            mockMvc.perform(put("/api/ingredients/{id}/cost", ingredientId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());
        }
    }

}
