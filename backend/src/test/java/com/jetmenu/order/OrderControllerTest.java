package com.jetmenu.order;

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
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@WithMockUser
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID orderId;
    private UUID customerId;
    private UUID productId;
    private OrderResponse orderResponse;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        productId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(UUID.randomUUID());

        OrderItemResponse itemResponse = OrderItemResponse.builder()
                .id(UUID.randomUUID())
                .productId(productId)
                .productName("Hambúrguer")
                .quantity(2)
                .unitPrice(new BigDecimal("30.00"))
                .build();

        orderResponse = OrderResponse.builder()
                .id(orderId)
                .dateTime(LocalDateTime.now())
                .customerId(customerId)
                .customerName("Cliente Teste")
                .status(OrderStatus.PAID)
                .totalValue(new BigDecimal("60.00"))
                .estimatedProfit(new BigDecimal("36.00"))
                .items(List.of(itemResponse))
                .build();
    }

    private OrderRequest buildValidRequest() {
        OrderItemRequest itemRequest = OrderItemRequest.builder()
                .productId(productId)
                .quantity(2)
                .build();

        return OrderRequest.builder()
                .customerId(customerId)
                .items(List.of(itemRequest))
                .build();
    }

    // -------------------------------------------------------------------------
    // POST /api/orders
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/orders")
    class CreateOrder {

        @Test
        @DisplayName("deve retornar 201 com OrderResponse ao criar pedido válido")
        void shouldReturn201WithOrderResponse() throws Exception {
            given(orderService.create(any(), any(OrderRequest.class))).willReturn(orderResponse);

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.customerId").value(customerId.toString()))
                    .andExpect(jsonPath("$.customerName").value("Cliente Teste"))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.totalValue").value(60.00))
                    .andExpect(jsonPath("$.items").isArray())
                    .andExpect(jsonPath("$.items[0].productName").value("Hambúrguer"));
        }

        @Test
        @DisplayName("deve aceitar ingrediente extra no item do pedido")
        void shouldAcceptExtraIngredientsOnCreate() throws Exception {
            OrderRequest requestWithExtra = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(2)
                            .extraIngredients(List.of(
                                    OrderItemExtraIngredientRequest.builder()
                                            .ingredientId(UUID.randomUUID())
                                            .quantity(new BigDecimal("1.5"))
                                            .build()
                            ))
                            .build()))
                    .build();

            given(orderService.create(any(), any(OrderRequest.class))).willReturn(orderResponse);

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestWithExtra)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(OrderRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando nem customerId nem customerName são informados")
        void shouldReturn400WhenNeitherCustomerIdNorCustomerNameProvided() throws Exception {
            OrderRequest noCustomer = OrderRequest.builder()
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .build()))
                    .build();

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(noCustomer)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.customerId").value("Cliente é obrigatório"));

            then(orderService).should(never()).create(any(), any(OrderRequest.class));
        }

        @Test
        @DisplayName("deve retornar 400 quando customerName contém apenas espaços")
        void shouldReturn400WhenCustomerNameIsBlank() throws Exception {
            OrderRequest blankName = OrderRequest.builder()
                    .customerName("   ")
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .build()))
                    .build();

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(blankName)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.customerId").value("Cliente é obrigatório"));

            then(orderService).should(never()).create(any(), any(OrderRequest.class));
        }

        @Test
        @DisplayName("deve retornar 201 quando apenas customerName é informado")
        void shouldCreate201WithCustomerNameOnly() throws Exception {
            OrderRequest nameOnly = OrderRequest.builder()
                    .customerName("Maria Clara")
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .build()))
                    .build();

            given(orderService.create(any(), any(OrderRequest.class))).willReturn(orderResponse);

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(nameOnly)))
                    .andExpect(status().isCreated());

            org.mockito.ArgumentCaptor<OrderRequest> captor =
                    org.mockito.ArgumentCaptor.forClass(OrderRequest.class);
            then(orderService).should().create(any(), captor.capture());
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getCustomerName())
                    .isEqualTo("Maria Clara");
            org.assertj.core.api.Assertions.assertThat(captor.getValue().getCustomerId()).isNull();
        }

        @Test
        @DisplayName("deve retornar 400 quando customerId é string vazia (payload legado)")
        void shouldReturn400WhenCustomerIdIsEmptyString() throws Exception {
            String legacyPayload = """
                    {"customerId":"","items":[{"productId":"%s","quantity":1}]}
                    """.formatted(productId);

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(legacyPayload))
                    .andExpect(status().isBadRequest());

            then(orderService).should(never()).create(any(), any(OrderRequest.class));
        }

        @Test
        @DisplayName("deve retornar 400 quando lista de itens está vazia")
        void shouldReturn400WhenItemsListIsEmpty() throws Exception {
            OrderRequest emptyItems = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of())
                    .build();

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(emptyItems)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando ingrediente extra possui campos inválidos")
        void shouldReturn400WhenExtraIngredientIsInvalid() throws Exception {
            // ingredientId ausente
            OrderRequest invalid = OrderRequest.builder()
                    .customerId(customerId)
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .extraIngredients(List.of(
                                    OrderItemExtraIngredientRequest.builder()
                                            .quantity(new BigDecimal("1"))
                                            .build()
                            ))
                            .build()))
                    .build();

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest());

            then(orderService).should(never()).create(any(), any(OrderRequest.class));
        }

        @Test
        @DisplayName("deve retornar 404 quando cliente não encontrado")
        void shouldReturn404WhenCustomerNotFound() throws Exception {
            given(orderService.create(any(), any(OrderRequest.class)))
                    .willThrow(new OrderNotFoundException("Cliente com ID " + customerId + " não encontrado"));

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 404 quando produto não encontrado")
        void shouldReturn404WhenProductNotFound() throws Exception {
            given(orderService.create(any(), any(OrderRequest.class)))
                    .willThrow(new OrderNotFoundException("Produto com ID " + productId + " não encontrado"));

            mockMvc.perform(post("/api/orders")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/orders/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/orders/{id}")
    class GetOrderById {

        @Test
        @DisplayName("deve retornar 200 com OrderResponse quando pedido existe")
        void shouldReturn200WhenOrderExists() throws Exception {
            given(orderService.findById(any(), eq(orderId))).willReturn(orderResponse);

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId.toString()))
                    .andExpect(jsonPath("$.status").value("PAID"))
                    .andExpect(jsonPath("$.totalValue").value(60.00));
        }

        @Test
        @DisplayName("deve retornar 404 quando pedido não encontrado")
        void shouldReturn404WhenOrderNotFound() throws Exception {
            given(orderService.findById(any(), eq(orderId))).willThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(get("/api/orders/{id}", orderId))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/orders
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/orders")
    class GetAllOrders {

        @Test
        @DisplayName("deve retornar 200 com página de pedidos")
        void shouldReturn200WithOrderPage() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderService.findAll(any(), eq(""), eq(null), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(orderResponse), pageable, 1));

            mockMvc.perform(get("/api/orders"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content[0].id").value(orderId.toString()))
                    .andExpect(jsonPath("$.content[0].status").value("PAID"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }

        @Test
        @DisplayName("deve repassar parâmetro search ao service")
        void shouldPassSearchParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderService.findAll(any(), eq("maria"), eq(null), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(orderResponse), pageable, 1));

            mockMvc.perform(get("/api/orders").param("search", "maria"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(orderId.toString()));
        }

        @Test
        @DisplayName("deve repassar parâmetro status ao service")
        void shouldPassStatusParamToService() throws Exception {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(orderService.findAll(any(), eq(""), eq(OrderStatus.READY), any(org.springframework.data.domain.Pageable.class)))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(
                            List.of(orderResponse), pageable, 1));

            mockMvc.perform(get("/api/orders").param("status", "READY"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/orders/status-counts")
    class StatusCounts {

        @Test
        @DisplayName("deve retornar contagens por status")
        void shouldReturnCountsByStatus() throws Exception {
            java.util.Map<OrderStatus, Long> counts = new java.util.EnumMap<>(OrderStatus.class);
            counts.put(OrderStatus.PENDING, 3L);
            counts.put(OrderStatus.READY, 1L);
            counts.put(OrderStatus.DELIVERED, 0L);
            counts.put(OrderStatus.PAID, 2L);
            counts.put(OrderStatus.CANCELLED, 0L);
            given(orderService.statusCounts(any(), any(), any(), eq(""))).willReturn(counts);

            mockMvc.perform(get("/api/orders/status-counts"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.PENDING").value(3))
                    .andExpect(jsonPath("$.READY").value(1))
                    .andExpect(jsonPath("$.PAID").value(2));
        }
    }

    // -------------------------------------------------------------------------
    // PUT /api/orders/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("PUT /api/orders/{id}")
    class UpdateOrder {

        @Test
        @DisplayName("deve retornar 200 com OrderResponse atualizado")
        void shouldReturn200WithUpdatedResponse() throws Exception {
            given(orderService.update(any(), eq(orderId), any(OrderRequest.class))).willReturn(orderResponse);

            mockMvc.perform(put("/api/orders/{id}", orderId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(orderId.toString()));
        }

        @Test
        @DisplayName("deve retornar 404 quando pedido não encontrado para atualização")
        void shouldReturn404WhenOrderNotFoundForUpdate() throws Exception {
            given(orderService.update(any(), eq(orderId), any(OrderRequest.class)))
                    .willThrow(new OrderNotFoundException(orderId));

            mockMvc.perform(put("/api/orders/{id}", orderId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildValidRequest())))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 400 quando campos obrigatórios estão ausentes")
        void shouldReturn400WhenRequiredFieldsMissing() throws Exception {
            mockMvc.perform(put("/api/orders/{id}", orderId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(OrderRequest.builder().build())))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 400 quando nem customerId nem customerName são informados na atualização")
        void shouldReturn400OnUpdateWhenNoCustomerReference() throws Exception {
            OrderRequest noCustomer = OrderRequest.builder()
                    .items(List.of(OrderItemRequest.builder()
                            .productId(productId)
                            .quantity(1)
                            .build()))
                    .build();

            mockMvc.perform(put("/api/orders/{id}", orderId)
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(noCustomer)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.fieldErrors.customerId").value("Cliente é obrigatório"));

            then(orderService).should(never()).update(any(), any(), any(OrderRequest.class));
        }
    }

    // -------------------------------------------------------------------------
    // DELETE /api/orders/{id}
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("DELETE /api/orders/{id}")
    class DeleteOrder {

        @Test
        @DisplayName("deve retornar 204 ao deletar pedido existente")
        void shouldReturn204WhenDeleted() throws Exception {
            willDoNothing().given(orderService).delete(any(), eq(orderId));

            mockMvc.perform(delete("/api/orders/{id}", orderId)
                            .with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("deve retornar 404 ao tentar deletar pedido inexistente")
        void shouldReturn404WhenOrderNotFoundForDelete() throws Exception {
            willThrow(new OrderNotFoundException(orderId)).given(orderService).delete(any(), eq(orderId));

            mockMvc.perform(delete("/api/orders/{id}", orderId)
                            .with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }
}

