package com.jetmenu.order;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.ingredient.IngredientNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Nota: como nos demais controller tests deste projeto, o merchantId é casado com
 * {@code any()} — o parâmetro {@code Authentication} chega nulo neste setup de
 * {@code @WebMvcTest} (em produção o {@code JwtAuthFilter} o preenche). O escopo por
 * lojista é coberto em {@link OrderFichaServiceTest}, que recebe o merchantId explícito.
 */
@WebMvcTest(OrderFichaController.class)
@WithMockUser
@DisplayName("OrderFichaController")
class OrderFichaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderFichaService orderFichaService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID merchantId;
    private UUID ingredientId;
    private OrderFichaResponse fichaResponse;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);

        fichaResponse = OrderFichaResponse.builder()
                .lines(List.of(OrderFichaLineResponse.builder()
                        .id(UUID.randomUUID())
                        .ingredientId(ingredientId)
                        .ingredientName("Sacola de entrega")
                        .ingredientUnit("un")
                        .quantity(BigDecimal.ONE)
                        .costPerUnit(new BigDecimal("0.50"))
                        .totalCost(new BigDecimal("0.50"))
                        .build()))
                .totalCost(new BigDecimal("0.50"))
                .build();
    }

    private OrderFichaRequest validRequest() {
        return OrderFichaRequest.builder()
                .lines(List.of(OrderFichaLineRequest.builder()
                        .ingredientId(ingredientId)
                        .quantity(BigDecimal.ONE)
                        .build()))
                .build();
    }

    @Nested
    @DisplayName("GET /api/order-ficha")
    class Find {

        @Test
        @DisplayName("devolve 200 com as linhas e o custo por pedido")
        void returnsFicha() throws Exception {
            given(orderFichaService.findByMerchant(any())).willReturn(fichaResponse);

            mockMvc.perform(get("/api/order-ficha"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lines[0].ingredientName").value("Sacola de entrega"))
                    .andExpect(jsonPath("$.lines[0].ingredientUnit").value("un"))
                    .andExpect(jsonPath("$.lines[0].totalCost").value(0.50))
                    .andExpect(jsonPath("$.totalCost").value(0.50));
        }
    }

    @Nested
    @DisplayName("PUT /api/order-ficha")
    class Replace {

        @Test
        @DisplayName("devolve 200 com a ficha atualizada")
        void replacesFicha() throws Exception {
            given(orderFichaService.replace(any(), any(OrderFichaRequest.class)))
                    .willReturn(fichaResponse);

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(0.50));
        }

        @Test
        @DisplayName("devolve 400 quando a quantidade é zero")
        void rejectsZeroQuantity() throws Exception {
            OrderFichaRequest request = OrderFichaRequest.builder()
                    .lines(List.of(OrderFichaLineRequest.builder()
                            .ingredientId(ingredientId)
                            .quantity(BigDecimal.ZERO)
                            .build()))
                    .build();

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(orderFichaService, never()).replace(any(), any());
        }

        @Test
        @DisplayName("devolve 400 quando o ingrediente não é informado")
        void rejectsMissingIngredient() throws Exception {
            OrderFichaRequest request = OrderFichaRequest.builder()
                    .lines(List.of(OrderFichaLineRequest.builder()
                            .quantity(BigDecimal.ONE)
                            .build()))
                    .build();

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());

            verify(orderFichaService, never()).replace(any(), any());
        }

        @Test
        @DisplayName("devolve 404 quando o ingrediente não existe para o lojista")
        void returnsNotFoundForUnknownIngredient() throws Exception {
            willThrow(new IngredientNotFoundException(ingredientId))
                    .given(orderFichaService).replace(any(), any(OrderFichaRequest.class));

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("devolve 409 quando o mesmo insumo é informado duas vezes")
        void returnsConflictForDuplicateIngredient() throws Exception {
            willThrow(new DuplicateOrderFichaIngredientException("Sacola de entrega"))
                    .given(orderFichaService).replace(any(), any(OrderFichaRequest.class));

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("lista vazia limpa a ficha e devolve 200")
        void allowsEmptyList() throws Exception {
            given(orderFichaService.replace(any(), any(OrderFichaRequest.class)))
                    .willReturn(OrderFichaResponse.builder().lines(List.of()).totalCost(BigDecimal.ZERO).build());

            mockMvc.perform(put("/api/order-ficha").with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    OrderFichaRequest.builder().lines(List.of()).build())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalCost").value(0));
        }
    }
}
