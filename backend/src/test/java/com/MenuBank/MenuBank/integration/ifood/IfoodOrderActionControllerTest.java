package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.auth.AuthHelper;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderActionResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderConfirmationWindowResponse;
import com.MenuBank.MenuBank.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodOrderActionController.class)
@WithMockUser
@DisplayName("IfoodOrderActionController")
class IfoodOrderActionControllerTest {

    private static final String BASE = "/api/integrations/ifood/orders";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IfoodOrderActionService actionService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    private IfoodOrderActionResponse response(OrderStatus status) {
        return new IfoodOrderActionResponse(orderId, "ord_1", status);
    }

    @Test
    @DisplayName("POST /{id}/confirm retorna 200 com o pedido ainda em PENDING")
    void confirm_returns200() throws Exception {
        given(actionService.confirm(merchantId, orderId)).willReturn(response(OrderStatus.PENDING));

        mockMvc.perform(post(BASE + "/" + orderId + "/confirm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.externalOrderId").value("ord_1"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        then(actionService).should().confirm(merchantId, orderId);
    }

    @Test
    @DisplayName("POST /{id}/ready-to-pickup retorna 200 com o pedido em READY")
    void readyToPickup_returns200() throws Exception {
        given(actionService.readyToPickup(merchantId, orderId)).willReturn(response(OrderStatus.READY));

        mockMvc.perform(post(BASE + "/" + orderId + "/ready-to-pickup").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        then(actionService).should().readyToPickup(merchantId, orderId);
    }

    @Test
    @DisplayName("POST /{id}/dispatch retorna 200 com o pedido em DELIVERED")
    void dispatch_returns200() throws Exception {
        given(actionService.dispatch(merchantId, orderId)).willReturn(response(OrderStatus.DELIVERED));

        mockMvc.perform(post(BASE + "/" + orderId + "/dispatch").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        then(actionService).should().dispatch(merchantId, orderId);
    }

    @Test
    @DisplayName("GET /{id}/confirmation-window retorna 200 com o prazo do SLA de 8 minutos")
    void confirmationWindow_returns200() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 7, 28, 12, 0, 0);
        given(actionService.getConfirmationWindow(merchantId, orderId)).willReturn(
                new IfoodOrderConfirmationWindowResponse(
                        orderId, createdAt, createdAt.plusMinutes(8), 300L, false));

        mockMvc.perform(get(BASE + "/" + orderId + "/confirmation-window"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.createdAt").value("2026-07-28T12:00:00"))
                .andExpect(jsonPath("$.deadline").value("2026-07-28T12:08:00"))
                .andExpect(jsonPath("$.remainingSeconds").value(300))
                .andExpect(jsonPath("$.expired").value(false));
    }

    // --- ProblemDetail mappings ------------------------------------------------------

    @Test
    @DisplayName("retorna 404 quando o pedido não existe para o merchant")
    void unknownOrder_returns404() throws Exception {
        given(actionService.confirm(merchantId, orderId)).willThrow(new IfoodOrderNotFoundException(orderId));

        mockMvc.perform(post(BASE + "/" + orderId + "/confirm").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido não encontrado."));
    }

    @Test
    @DisplayName("retorna 409 quando o pedido não é do iFood")
    void nonIfoodOrder_returns409() throws Exception {
        given(actionService.confirm(merchantId, orderId)).willThrow(new IfoodOrderActionNotAllowedException(
                IfoodOrderActionNotAllowedException.Reason.NOT_IFOOD_ORDER));

        mockMvc.perform(post(BASE + "/" + orderId + "/confirm").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Esta ação só está disponível para pedidos do iFood."));
    }

    @Test
    @DisplayName("retorna 409 quando o pedido do iFood não tem identificador externo")
    void missingExternalId_returns409() throws Exception {
        given(actionService.dispatch(merchantId, orderId)).willThrow(new IfoodOrderActionNotAllowedException(
                IfoodOrderActionNotAllowedException.Reason.MISSING_EXTERNAL_ID));

        mockMvc.perform(post(BASE + "/" + orderId + "/dispatch").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Este pedido não tem identificador do iFood, então não é possível atualizá-lo."));
    }

    @Test
    @DisplayName("retorna 409 quando o pedido está cancelado ou é de teste")
    void terminalStatus_returns409() throws Exception {
        given(actionService.readyToPickup(merchantId, orderId)).willThrow(new IfoodOrderActionNotAllowedException(
                IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS));

        mockMvc.perform(post(BASE + "/" + orderId + "/ready-to-pickup").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Pedido cancelado ou de teste não pode ser atualizado no iFood."));
    }

    @Test
    @DisplayName("retorna 409 ao marcar como pronto para retirada um pedido que não é de retirada")
    void readyToPickupOnNonTakeout_returns409() throws Exception {
        given(actionService.readyToPickup(merchantId, orderId)).willThrow(new IfoodOrderActionNotAllowedException(
                IfoodOrderActionNotAllowedException.Reason.NOT_A_TAKEOUT_ORDER));

        mockMvc.perform(post(BASE + "/" + orderId + "/ready-to-pickup").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Só é possível marcar como pronto para retirada um pedido de retirada."));
    }

    @Test
    @DisplayName("retorna 409 ao despachar um pedido que não é de entrega")
    void dispatchOnNonDelivery_returns409() throws Exception {
        given(actionService.dispatch(merchantId, orderId)).willThrow(new IfoodOrderActionNotAllowedException(
                IfoodOrderActionNotAllowedException.Reason.NOT_A_DELIVERY_ORDER));

        mockMvc.perform(post(BASE + "/" + orderId + "/dispatch").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Só é possível despachar um pedido de entrega."));
    }

    @Test
    @DisplayName("retorna 404 quando o iFood não encontra o pedido")
    void notFoundOnIfood_returns404() throws Exception {
        given(actionService.dispatch(merchantId, orderId)).willThrow(new IfoodResourceNotFoundException());

        mockMvc.perform(post(BASE + "/" + orderId + "/dispatch").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido não encontrado no iFood."));
    }

    @Test
    @DisplayName("retorna 400 com o detalhe do iFood quando a ação é recusada")
    void rejectedByIfood_returns400() throws Exception {
        given(actionService.confirm(merchantId, orderId))
                .willThrow(new IfoodBadRequestException("order already confirmed"));

        mockMvc.perform(post(BASE + "/" + orderId + "/confirm").with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("O iFood recusou a ação: order already confirmed"));
    }

    @Test
    @DisplayName("retorna 409 quando a autorização com o iFood expirou")
    void reauthorizationRequired_returns409() throws Exception {
        given(actionService.confirm(merchantId, orderId))
                .willThrow(new IfoodReauthorizationRequiredException());

        mockMvc.perform(post(BASE + "/" + orderId + "/confirm").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A autorização com o iFood expirou. Reconecte sua conta e tente novamente."));
    }
}
