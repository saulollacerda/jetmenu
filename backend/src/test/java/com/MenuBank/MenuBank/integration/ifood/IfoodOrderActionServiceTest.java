package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.integration.ifood.dto.IfoodCancellationReasonResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderActionResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderCancelRequest;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderConfirmationWindowResponse;
import com.MenuBank.MenuBank.order.Order;
import com.MenuBank.MenuBank.order.OrderOrigin;
import com.MenuBank.MenuBank.order.OrderRepository;
import com.MenuBank.MenuBank.order.OrderStatus;
import com.MenuBank.MenuBank.order.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodOrderActionService")
class IfoodOrderActionServiceTest {

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    @Mock private IfoodOrderActionClient actionClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private OrderRepository orderRepository;

    @InjectMocks
    private IfoodOrderActionService actionService;

    private UUID merchantId;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        lenient().when(tokenService.getAccessToken()).thenReturn("token-1");
    }

    /** Order with an unknown (null) orderType — the shape of everything imported before V27. */
    private Order ifoodOrder(OrderStatus status) {
        return ifoodOrder(status, null);
    }

    private Order ifoodOrder(OrderStatus status, OrderType orderType) {
        return Order.builder()
                .id(orderId)
                .status(status)
                .origin(OrderOrigin.IFOOD)
                .externalOrderId("ord_1")
                .orderType(orderType)
                .dateTime(LocalDateTime.now(BRAZIL_ZONE))
                .build();
    }

    private void givenOrder(Order order) {
        given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.of(order));
    }

    private static HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    private static HttpClientErrorException clientError(HttpStatus status, String body) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    @Nested
    @DisplayName("confirm")
    class Confirm {

        @Test
        @DisplayName("chama o iFood com o id externo e mantém o pedido em PENDING")
        void confirm_shouldCallIfoodAndKeepOrderPending() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));

            IfoodOrderActionResponse response = actionService.confirm(merchantId, orderId);

            then(actionClient).should().confirm("token-1", "ord_1");
            then(orderRepository).should(never()).save(any());
            assertThat(response.orderId()).isEqualTo(orderId);
            assertThat(response.externalOrderId()).isEqualTo("ord_1");
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("renova o token e repete uma única vez quando o iFood responde 401")
        void confirm_shouldRefreshTokenAndRetryOnceOn401() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            given(tokenService.handleUnauthorized()).willReturn("token-2");
            doThrow(unauthorized()).doNothing().when(actionClient).confirm(any(), any());

            actionService.confirm(merchantId, orderId);

            then(tokenService).should().handleUnauthorized();
            then(actionClient).should().confirm("token-1", "ord_1");
            then(actionClient).should().confirm("token-2", "ord_1");
            then(actionClient).should(times(2)).confirm(any(), any());
        }

        @Test
        @DisplayName("repete uma vez em falha transitória (5xx) para não perder o SLA de 8 minutos")
        void confirm_shouldRetryOnceOnTransientServerError() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            doThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))
                    .doNothing().when(actionClient).confirm(any(), any());

            IfoodOrderActionResponse response = actionService.confirm(merchantId, orderId);

            then(actionClient).should(times(2)).confirm("token-1", "ord_1");
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("propaga a falha transitória quando as duas tentativas falham")
        void confirm_shouldGiveUpAfterTwoTransientFailures() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            willThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))
                    .given(actionClient).confirm(any(), any());

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(HttpServerErrorException.class);

            then(actionClient).should(times(2)).confirm("token-1", "ord_1");
        }

        @Test
        @DisplayName("não repete em 4xx — o iFood recusou a ação, repetir só duplicaria o erro")
        void confirm_shouldNotRetryOnClientError() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"order already confirmed\"}"))
                    .given(actionClient).confirm(any(), any());

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodBadRequestException.class);

            then(actionClient).should(times(1)).confirm(any(), any());
        }
    }

    @Nested
    @DisplayName("readyToPickup")
    class ReadyToPickup {

        @Test
        @DisplayName("chama o iFood e move o pedido para READY")
        void readyToPickup_shouldCallIfoodAndMoveOrderToReady() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            doNothing().when(actionClient).readyToPickup("token-1", "ord_1");

            IfoodOrderActionResponse response = actionService.readyToPickup(merchantId, orderId);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.READY);
            assertThat(response.status()).isEqualTo(OrderStatus.READY);
        }
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("chama o iFood e move o pedido para DELIVERED")
        void dispatch_shouldCallIfoodAndMoveOrderToDelivered() {
            Order order = ifoodOrder(OrderStatus.READY);
            givenOrder(order);
            doNothing().when(actionClient).dispatch("token-1", "ord_1");

            IfoodOrderActionResponse response = actionService.dispatch(merchantId, orderId);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.DELIVERED);
            assertThat(response.status()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("não repete em falha transitória — só o confirm tem SLA e retry curto")
        void dispatch_shouldNotRetryOnTransientServerError() {
            givenOrder(ifoodOrder(OrderStatus.READY));
            willThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable",
                    HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8))
                    .given(actionClient).dispatch(any(), any());

            assertThatThrownBy(() -> actionService.dispatch(merchantId, orderId))
                    .isInstanceOf(HttpServerErrorException.class);

            then(actionClient).should(times(1)).dispatch(any(), any());
            then(orderRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("motivos de cancelamento")
    class CancellationReasons {

        @Test
        @DisplayName("devolve os motivos do iFood com código e descrição")
        void reasons_shouldReturnIfoodReasons() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            given(actionClient.cancellationReasons("token-1", "ord_1")).willReturn(List.of(
                    new IfoodCancellationReasonResponse("501", "PROBLEMAS DE SISTEMA"),
                    new IfoodCancellationReasonResponse("506", "ITEM INDISPONÍVEL")));

            List<IfoodCancellationReasonResponse> reasons =
                    actionService.getCancellationReasons(merchantId, orderId);

            assertThat(reasons).hasSize(2);
            assertThat(reasons.get(0).cancelCodeId()).isEqualTo("501");
            assertThat(reasons.get(1).description()).isEqualTo("ITEM INDISPONÍVEL");
        }

        @Test
        @DisplayName("renova o token e repete uma única vez quando o iFood responde 401")
        void reasons_shouldRefreshTokenAndRetryOnceOn401() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            given(tokenService.handleUnauthorized()).willReturn("token-2");
            given(actionClient.cancellationReasons("token-1", "ord_1")).willThrow(unauthorized());
            given(actionClient.cancellationReasons("token-2", "ord_1")).willReturn(List.of());

            assertThat(actionService.getCancellationReasons(merchantId, orderId)).isEmpty();

            then(tokenService).should().handleUnauthorized();
        }

        @Test
        @DisplayName("rejeita pedido já cancelado — não há motivo a escolher")
        void reasons_shouldRejectCancelledOrder() {
            givenOrder(ifoodOrder(OrderStatus.CANCELLED));

            assertThatThrownBy(() -> actionService.getCancellationReasons(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);

            then(actionClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("cancelamento pedido pelo lojista")
    class RequestCancellation {

        @Test
        @DisplayName("envia o código do motivo ao iFood e só então cancela o pedido local")
        void cancel_shouldCallIfoodAndCancelLocalOrder() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            doNothing().when(actionClient)
                    .requestCancellation("token-1", "ord_1", "501", "PROBLEMAS DE SISTEMA");

            IfoodOrderActionResponse response = actionService.cancel(merchantId, orderId,
                    new IfoodOrderCancelRequest("501", "PROBLEMAS DE SISTEMA"));

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("pedido recusado pelo iFood não cancela o pedido local")
        void cancel_shouldLeaveOrderUntouchedWhenIfoodRejects() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"cancellation not allowed\"}"))
                    .given(actionClient).requestCancellation("token-1", "ord_1", "501", null);

            assertThatThrownBy(() -> actionService.cancel(merchantId, orderId,
                    new IfoodOrderCancelRequest("501", null)))
                    .isInstanceOf(IfoodBadRequestException.class)
                    .extracting("detail")
                    .isEqualTo("cancellation not allowed");

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            then(orderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("pedido já cancelado é rejeitado antes de chamar o iFood")
        void cancel_shouldRejectCancelledOrder() {
            givenOrder(ifoodOrder(OrderStatus.CANCELLED));

            assertThatThrownBy(() -> actionService.cancel(merchantId, orderId,
                    new IfoodOrderCancelRequest("501", null)))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);

            then(actionClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("resposta à solicitação de cancelamento do cliente")
    class AnswerCancellationRequest {

        @Test
        @DisplayName("aceitar chama o iFood e cancela o pedido local")
        void accept_shouldCallIfoodAndCancelLocalOrder() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            doNothing().when(actionClient).acceptCancellation("token-1", "ord_1");

            IfoodOrderActionResponse response = actionService.acceptCancellation(merchantId, orderId);

            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
            assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("recusar chama o iFood e mantém o status local intacto")
        void deny_shouldCallIfoodAndKeepLocalStatus() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            doNothing().when(actionClient).denyCancellation("token-1", "ord_1");

            IfoodOrderActionResponse response = actionService.denyCancellation(merchantId, orderId);

            then(orderRepository).should(never()).save(any());
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(response.status()).isEqualTo(OrderStatus.PENDING);
        }

        @Test
        @DisplayName("aceite recusado pelo iFood não cancela o pedido local")
        void accept_shouldLeaveOrderUntouchedWhenIfoodRejects() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            givenOrder(order);
            willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"no cancellation request\"}"))
                    .given(actionClient).acceptCancellation("token-1", "ord_1");

            assertThatThrownBy(() -> actionService.acceptCancellation(merchantId, orderId))
                    .isInstanceOf(IfoodBadRequestException.class);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            then(orderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("pedido de teste é rejeitado antes de chamar o iFood")
        void deny_shouldRejectTestOrder() {
            givenOrder(ifoodOrder(OrderStatus.TEST));

            assertThatThrownBy(() -> actionService.denyCancellation(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);

            then(actionClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("guard rails")
    class GuardRails {

        @Test
        @DisplayName("pedido inexistente para o merchant gera IfoodOrderNotFoundException")
        void unknownOrder_shouldThrowNotFound() {
            given(orderRepository.findByIdAndMerchantId(orderId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodOrderNotFoundException.class);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("pedido que não é do iFood é rejeitado")
        void nonIfoodOrder_shouldBeRejected() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            order.setOrigin(OrderOrigin.MENUBANK);
            givenOrder(order);

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.NOT_IFOOD_ORDER);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("pedido do iFood sem externalOrderId é rejeitado")
        void missingExternalOrderId_shouldBeRejected() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            order.setExternalOrderId(null);
            givenOrder(order);

            assertThatThrownBy(() -> actionService.dispatch(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.MISSING_EXTERNAL_ID);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("pedido cancelado é rejeitado")
        void cancelledOrder_shouldBeRejected() {
            givenOrder(ifoodOrder(OrderStatus.CANCELLED));

            assertThatThrownBy(() -> actionService.readyToPickup(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("pedido de teste é rejeitado")
        void testOrder_shouldBeRejected() {
            givenOrder(ifoodOrder(OrderStatus.TEST));

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);

            then(actionClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("guard rails de tipo do pedido")
    class OrderTypeGuardRails {

        @Test
        @DisplayName("readyToPickup aceita pedido TAKEOUT")
        void readyToPickup_shouldAcceptTakeout() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.TAKEOUT));

            actionService.readyToPickup(merchantId, orderId);

            then(actionClient).should().readyToPickup("token-1", "ord_1");
        }

        @Test
        @DisplayName("readyToPickup rejeita pedido DELIVERY")
        void readyToPickup_shouldRejectDelivery() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.DELIVERY));

            assertThatThrownBy(() -> actionService.readyToPickup(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.NOT_A_TAKEOUT_ORDER);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("readyToPickup rejeita pedido DINE_IN")
        void readyToPickup_shouldRejectDineIn() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.DINE_IN));

            assertThatThrownBy(() -> actionService.readyToPickup(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.NOT_A_TAKEOUT_ORDER);
        }

        @Test
        @DisplayName("dispatch aceita pedido DELIVERY")
        void dispatch_shouldAcceptDelivery() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.DELIVERY));

            actionService.dispatch(merchantId, orderId);

            then(actionClient).should().dispatch("token-1", "ord_1");
        }

        @Test
        @DisplayName("dispatch rejeita pedido TAKEOUT")
        void dispatch_shouldRejectTakeout() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.TAKEOUT));

            assertThatThrownBy(() -> actionService.dispatch(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class)
                    .extracting("reason")
                    .isEqualTo(IfoodOrderActionNotAllowedException.Reason.NOT_A_DELIVERY_ORDER);

            then(actionClient).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("tipo desconhecido (null, pedidos anteriores à V27) não bloqueia nenhuma ação")
        void unknownOrderType_shouldNotBlockAnyAction() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, null));

            actionService.readyToPickup(merchantId, orderId);
            actionService.dispatch(merchantId, orderId);

            then(actionClient).should().readyToPickup("token-1", "ord_1");
            then(actionClient).should().dispatch("token-1", "ord_1");
        }

        @Test
        @DisplayName("confirm não depende do tipo — o SLA vale para DELIVERY e TAKEOUT")
        void confirm_shouldIgnoreOrderType() {
            givenOrder(ifoodOrder(OrderStatus.PENDING, OrderType.DINE_IN));

            actionService.confirm(merchantId, orderId);

            then(actionClient).should().confirm("token-1", "ord_1");
        }
    }

    @Nested
    @DisplayName("erros do iFood")
    class IfoodErrors {

        @Test
        @DisplayName("404 do iFood vira IfoodResourceNotFoundException e não muda o pedido")
        void notFoundFromIfood_shouldTranslateAndLeaveOrderUntouched() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            willThrow(clientError(HttpStatus.NOT_FOUND, "{\"message\":\"order not found\"}"))
                    .given(actionClient).dispatch("token-1", "ord_1");

            assertThatThrownBy(() -> actionService.dispatch(merchantId, orderId))
                    .isInstanceOf(IfoodResourceNotFoundException.class);

            then(orderRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("400 do iFood vira IfoodBadRequestException com o detalhe da API")
        void badRequestFromIfood_shouldCarryDetail() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"order already confirmed\"}"))
                    .given(actionClient).confirm("token-1", "ord_1");

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodBadRequestException.class)
                    .extracting("detail")
                    .isEqualTo("order already confirmed");
        }

        @Test
        @DisplayName("401 persistente após o refresh vira IfoodReauthorizationRequiredException")
        void repeatedUnauthorized_shouldRequireReauthorization() {
            givenOrder(ifoodOrder(OrderStatus.PENDING));
            given(tokenService.handleUnauthorized()).willReturn("token-2");
            willThrow(unauthorized()).given(actionClient).confirm(any(), any());

            assertThatThrownBy(() -> actionService.confirm(merchantId, orderId))
                    .isInstanceOf(IfoodReauthorizationRequiredException.class);
        }
    }

    @Nested
    @DisplayName("janela de confirmação (SLA de 8 minutos)")
    class ConfirmationWindow {

        @Test
        @DisplayName("expõe o prazo e o tempo restante de um pedido recém-criado")
        void window_shouldExposeDeadlineAndRemainingTime() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            LocalDateTime createdAt = LocalDateTime.now(BRAZIL_ZONE).minusMinutes(3);
            order.setDateTime(createdAt);
            givenOrder(order);

            IfoodOrderConfirmationWindowResponse window =
                    actionService.getConfirmationWindow(merchantId, orderId);

            assertThat(window.orderId()).isEqualTo(orderId);
            assertThat(window.createdAt()).isEqualTo(createdAt);
            assertThat(window.deadline()).isEqualTo(createdAt.plusMinutes(8));
            assertThat(window.remainingSeconds()).isBetween(290L, 300L);
            assertThat(window.expired()).isFalse();
        }

        @Test
        @DisplayName("zera o tempo restante e marca expirado quando o SLA já passou")
        void window_shouldClampToZeroWhenSlaExpired() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            order.setDateTime(LocalDateTime.now(BRAZIL_ZONE).minusMinutes(20));
            givenOrder(order);

            IfoodOrderConfirmationWindowResponse window =
                    actionService.getConfirmationWindow(merchantId, orderId);

            assertThat(window.remainingSeconds()).isZero();
            assertThat(window.expired()).isTrue();
        }

        @Test
        @DisplayName("rejeita pedido que não é do iFood")
        void window_shouldRejectNonIfoodOrder() {
            Order order = ifoodOrder(OrderStatus.PENDING);
            order.setOrigin(OrderOrigin.ANOTA_AI);
            givenOrder(order);

            assertThatThrownBy(() -> actionService.getConfirmationWindow(merchantId, orderId))
                    .isInstanceOf(IfoodOrderActionNotAllowedException.class);
        }
    }
}
