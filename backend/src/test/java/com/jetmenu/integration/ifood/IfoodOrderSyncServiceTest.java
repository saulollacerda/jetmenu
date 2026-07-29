package com.jetmenu.integration.ifood;

import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.ifood.dto.IfoodEventResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import com.jetmenu.integration.ifood.services.IfoodOrderImportService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderStatus;
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
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodOrderSyncService")
class IfoodOrderSyncServiceTest {

    @Mock private IfoodOrderClient orderClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private MerchantRepository merchantRepository;
    @Mock private IfoodOrderImportService importService;
    @Mock private IfoodProcessedEventRepository processedEventRepository;

    @InjectMocks
    private IfoodOrderSyncService syncService;

    private Merchant merchant;

    /** Backoff pauses recorded instead of slept, so the suite stays fast. */
    private final List<Long> backoffPauses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .build();
        merchant.setIfoodMerchantId("ifood-m1");
        merchant.setIfoodOrderSyncEnabled(true);
        lenient().when(merchantRepository.findAllByIfoodMerchantIdIsNotNullAndIfoodOrderSyncEnabledTrue())
                .thenReturn(List.of(merchant));
        lenient().when(tokenService.getAccessToken()).thenReturn("token-1");
        backoffPauses.clear();
        syncService.setBackoff(backoffPauses::add);
    }

    private IfoodEventResponse event(String id, String fullCode, String orderId) {
        IfoodEventResponse event = new IfoodEventResponse();
        event.setId(id);
        event.setCode(fullCode.substring(0, Math.min(3, fullCode.length())));
        event.setFullCode(fullCode);
        event.setOrderId(orderId);
        event.setMerchantId("ifood-m1");
        return event;
    }

    private IfoodOrderDetailResponse detail(String orderId) {
        IfoodOrderDetailResponse detail = new IfoodOrderDetailResponse();
        detail.setId(orderId);
        return detail;
    }

    private RawJsonResponse<IfoodOrderDetailResponse> raw(IfoodOrderDetailResponse detail) {
        return new RawJsonResponse<>(detail, rawOf(detail));
    }

    private static String rawOf(IfoodOrderDetailResponse detail) {
        return "{\"id\":\"" + detail.getId() + "\"}";
    }

    private static HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    private static HttpClientErrorException notFound() {
        return HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    private static HttpClientErrorException badRequest() {
        return HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("não chama a API quando nenhum merchant tem iFood autorizado com sincronia ativa")
    void shouldNotCallApiWhenNoAuthorizedMerchants() {
        given(merchantRepository.findAllByIfoodMerchantIdIsNotNullAndIfoodOrderSyncEnabledTrue())
                .willReturn(List.of());

        syncService.syncOrders();

        then(tokenService).should(never()).getAccessToken();
        then(orderClient).should(never()).pollEvents(anyString(), anyList());
    }

    @Test
    @DisplayName("consulta apenas merchants com sincronia habilitada — conectado mas desativado fica de fora")
    void shouldPollOnlySyncEnabledMerchants() {
        given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willReturn(List.of());

        syncService.syncOrders();

        then(merchantRepository).should().findAllByIfoodMerchantIdIsNotNullAndIfoodOrderSyncEnabledTrue();
        then(merchantRepository).should(never()).findAllByIfoodMerchantIdIsNotNull();
    }

    @Nested
    @DisplayName("evento CONFIRMED")
    class ConfirmedEvent {

        @Test
        @DisplayName("busca o detalhe e importa com status PENDING")
        void shouldImportConfirmedOrderAsPending() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONFIRMED", "ord-1")));
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("aceita a variante ORDER_CONFIRMED (case-insensitive)")
        void shouldAcceptOrderPrefixedVariant() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "order_confirmed", "ord-1")));
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));
        }

        @Test
        @DisplayName("404 no detalhe é pulado sem importar — o CONCLUDED importa depois (safety net)")
        void shouldSkipConfirmedWhenDetailNotFound() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONFIRMED", "ord-1")));
            given(orderClient.getOrderDetail("token-1", "ord-1")).willThrow(notFound());

            syncService.syncOrders();

            then(importService).should(never()).importOrder(any(), any(), any());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }
    }

    @Nested
    @DisplayName("evento CONCLUDED")
    class ConcludedEvent {

        @Test
        @DisplayName("pedido existente é atualizado para PAID sem buscar o detalhe")
        void shouldConcludeExistingOrderWithoutFetchingDetail() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONCLUDED", "ord-1")));
            given(importService.concludeOrder("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(orderClient).should(never()).getOrderDetail(anyString(), anyString());
            then(importService).should(never()).importOrder(any(), any(), any());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("pedido inexistente é importado completo com status PAID (fallback)")
        void shouldImportUnknownConcludedOrderAsPaid() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONCLUDED", "ord-1")));
            given(importService.concludeOrder("ord-1", "ifood-m1")).willReturn(false);
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.PAID, rawOf(detail));
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("aceita a variante ORDER_CONCLUDED (case-insensitive)")
        void shouldAcceptOrderPrefixedVariant() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "ORDER_CONCLUDED", "ord-1")));
            given(importService.concludeOrder("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(importService).should().concludeOrder("ord-1", "ifood-m1");
        }
    }

    @Nested
    @DisplayName("evento CANCELLED")
    class CancelledEvent {

        @Test
        @DisplayName("pedido existente é cancelado sem buscar o detalhe")
        void shouldCancelExistingOrderWithoutFetchingDetail() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CANCELLED", "ord-1")));
            given(importService.cancelOrder("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(orderClient).should(never()).getOrderDetail(anyString(), anyString());
            then(importService).should(never()).importOrder(any(), any(), any());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("pedido inexistente é importado com status CANCELLED")
        void shouldImportUnknownCancelledOrder() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CANCELLED", "ord-1")));
            given(importService.cancelOrder("ord-1", "ifood-m1")).willReturn(false);
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.CANCELLED, rawOf(detail));
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("404 no detalhe de pedido inexistente é logado e pulado, mas o evento é reconhecido")
        void shouldSkipUnknownCancelledOrderWhenDetailNotFound() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CANCELLED", "ord-1")));
            given(importService.cancelOrder("ord-1", "ifood-m1")).willReturn(false);
            given(orderClient.getOrderDetail("token-1", "ord-1")).willThrow(notFound());

            syncService.syncOrders();

            then(importService).should(never()).importOrder(any(), any(), any());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("aceita a variante ORDER_CANCELLED (case-insensitive)")
        void shouldAcceptOrderPrefixedVariant() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "Order_Cancelled", "ord-1")));
            given(importService.cancelOrder("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(importService).should().cancelOrder("ord-1", "ifood-m1");
        }
    }

    @Nested
    @DisplayName("evento CANCELLATION_REQUESTED")
    class CancellationRequestedEvent {

        @Test
        @DisplayName("notifica o lojista sem buscar o detalhe nem mexer no status do pedido")
        void shouldRegisterRequestOnExistingOrderWithoutFetchingDetail() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CANCELLATION_REQUESTED", "ord-1")));
            given(importService.registerCancellationRequest("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(orderClient).should(never()).getOrderDetail(anyString(), anyString());
            then(importService).should(never()).importOrder(any(), any(), any());
            then(importService).should(never()).cancelOrder(anyString(), anyString());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("pedido ainda não importado é importado como PENDING antes de registrar a solicitação")
        void shouldImportUnknownOrderBeforeRegisteringRequest() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CANCELLATION_REQUESTED", "ord-1")));
            given(importService.registerCancellationRequest("ord-1", "ifood-m1"))
                    .willReturn(false)
                    .willReturn(true);
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));
            then(importService).should(times(2)).registerCancellationRequest("ord-1", "ifood-m1");
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("aceita a variante ORDER_CANCELLATION_REQUESTED (case-insensitive)")
        void shouldAcceptOrderPrefixedVariant() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "order_cancellation_requested", "ord-1")));
            given(importService.registerCancellationRequest("ord-1", "ifood-m1")).willReturn(true);

            syncService.syncOrders();

            then(importService).should().registerCancellationRequest("ord-1", "ifood-m1");
        }
    }

    @Test
    @DisplayName("evento fora de CONFIRMED/CANCELLED/CONCLUDED é apenas reconhecido, sem ação")
    void shouldOnlyAcknowledgeIgnoredEvents() {
        given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                .willReturn(List.of(
                        event("evt-2", "PLACED", "ord-2"),
                        event("evt-3", "DISPATCHED", "ord-3")));

        syncService.syncOrders();

        then(orderClient).should(never()).getOrderDetail(anyString(), anyString());
        then(importService).should(never()).importOrder(any(), any(), any());
        then(importService).should(never()).concludeOrder(anyString(), anyString());
        then(importService).should(never()).cancelOrder(anyString(), anyString());
        then(importService).should(never()).registerCancellationRequest(anyString(), anyString());
        then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-2", "evt-3"));
    }

    @Test
    @DisplayName("falha em um pedido não impede o processamento dos demais nem o acknowledgment")
    void shouldContinueProcessingWhenOneOrderFails() {
        IfoodOrderDetailResponse detail3 = detail("ord-3");
        given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                .willReturn(List.of(
                        event("evt-1", "CONCLUDED", "ord-1"),
                        event("evt-3", "CONCLUDED", "ord-3")));
        given(importService.concludeOrder(anyString(), anyString())).willReturn(false);
        given(orderClient.getOrderDetail("token-1", "ord-1"))
                .willThrow(new RuntimeException("boom"));
        given(orderClient.getOrderDetail("token-1", "ord-3")).willReturn(raw(detail3));

        syncService.syncOrders();

        then(importService).should().importOrder(detail3, OrderStatus.PAID, rawOf(detail3));
        then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1", "evt-3"));
    }

    @Test
    @DisplayName("401 no polling força refresh do token e repete a chamada uma única vez")
    void shouldRetryPollOnceOn401() {
        given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willThrow(unauthorized());
        given(tokenService.handleUnauthorized()).willReturn("token-2");
        given(orderClient.pollEvents("token-2", List.of("ifood-m1"))).willReturn(List.of());

        syncService.syncOrders();

        then(tokenService).should().handleUnauthorized();
        then(orderClient).should().pollEvents("token-2", List.of("ifood-m1"));
    }

    @Test
    @DisplayName("não reconhece nada quando o polling não retorna eventos")
    void shouldNotAcknowledgeWhenNoEvents() {
        given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willReturn(List.of());

        syncService.syncOrders();

        then(orderClient).should(never()).acknowledgeEvents(anyString(), anyList());
    }

    @Nested
    @DisplayName("deduplicação de eventos")
    class EventDeduplication {

        @Test
        @DisplayName("evento já processado é descartado, mas continua sendo reconhecido")
        void shouldDiscardAlreadyProcessedEventButStillAcknowledgeIt() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONFIRMED", "ord-1")));
            given(processedEventRepository.findExistingIds(List.of("evt-1")))
                    .willReturn(List.of("evt-1"));

            syncService.syncOrders();

            then(orderClient).should(never()).getOrderDetail(anyString(), anyString());
            then(importService).should(never()).importOrder(any(), any(), any());
            then(processedEventRepository).should(never()).saveAll(any());
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1"));
        }

        @Test
        @DisplayName("evento novo é processado e registrado com a data de processamento")
        void shouldProcessAndRegisterNewEvent() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONFIRMED", "ord-1")));
            given(processedEventRepository.findExistingIds(List.of("evt-1"))).willReturn(List.of());
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));

            ArgumentCaptor<List<IfoodProcessedEvent>> captor = ArgumentCaptor.forClass(List.class);
            then(processedEventRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
            assertThat(captor.getValue().get(0).getEventId()).isEqualTo("evt-1");
            assertThat(captor.getValue().get(0).getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("em um lote misto apenas os eventos inéditos são processados e registrados")
        void shouldProcessOnlyUnseenEventsInMixedBatch() {
            IfoodOrderDetailResponse detail = detail("ord-2");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(
                            event("evt-1", "CONFIRMED", "ord-1"),
                            event("evt-2", "CONFIRMED", "ord-2")));
            given(processedEventRepository.findExistingIds(List.of("evt-1", "evt-2")))
                    .willReturn(List.of("evt-1"));
            given(orderClient.getOrderDetail("token-1", "ord-2")).willReturn(raw(detail));

            syncService.syncOrders();

            then(orderClient).should(never()).getOrderDetail("token-1", "ord-1");
            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));

            ArgumentCaptor<List<IfoodProcessedEvent>> captor = ArgumentCaptor.forClass(List.class);
            then(processedEventRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue()).extracting(IfoodProcessedEvent::getEventId)
                    .containsExactly("evt-2");
            then(orderClient).should().acknowledgeEvents("token-1", List.of("evt-1", "evt-2"));
        }

        @Test
        @DisplayName("id repetido dentro do mesmo lote de polling é processado uma única vez")
        void shouldProcessRepeatedIdWithinTheSameBatchOnlyOnce() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(
                            event("evt-1", "CONFIRMED", "ord-1"),
                            event("evt-1", "CONFIRMED", "ord-1")));
            given(processedEventRepository.findExistingIds(List.of("evt-1", "evt-1")))
                    .willReturn(List.of());
            given(orderClient.getOrderDetail("token-1", "ord-1")).willReturn(raw(detail));

            syncService.syncOrders();

            then(orderClient).should().getOrderDetail("token-1", "ord-1");
            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));

            ArgumentCaptor<List<IfoodProcessedEvent>> captor = ArgumentCaptor.forClass(List.class);
            then(processedEventRepository).should().saveAll(captor.capture());
            assertThat(captor.getValue()).hasSize(1);
        }

        @Test
        @DisplayName("expurga os ids processados há mais de 7 dias a cada execução")
        void shouldPurgeEventIdsOlderThanSevenDays() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willReturn(List.of());
            LocalDateTime before = LocalDateTime.now().minusDays(7);

            syncService.syncOrders();

            ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
            then(processedEventRepository).should().deleteProcessedBefore(captor.capture());
            assertThat(captor.getValue())
                    .isAfterOrEqualTo(before)
                    .isBeforeOrEqualTo(LocalDateTime.now().minusDays(7).plusSeconds(1));
        }
    }

    @Nested
    @DisplayName("retry com backoff em falhas transitórias")
    class TransientFailureRetry {

        @Test
        @DisplayName("5xx no polling é repetido com backoff exponencial até obter sucesso")
        void shouldRetryPollingOnServerError() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willThrow(serverError())
                    .willReturn(List.of());

            syncService.syncOrders();

            then(orderClient).should(times(2)).pollEvents("token-1", List.of("ifood-m1"));
            assertThat(backoffPauses).containsExactly(500L);
        }

        @Test
        @DisplayName("desiste após 3 tentativas e propaga o erro, com backoff de 500ms e 1s")
        void shouldGiveUpAfterThreeAttempts() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willThrow(serverError());

            assertThatThrownBy(() -> syncService.syncOrders())
                    .isInstanceOf(HttpServerErrorException.class);

            then(orderClient).should(times(3)).pollEvents("token-1", List.of("ifood-m1"));
            assertThat(backoffPauses).containsExactly(500L, 1000L);
        }

        @Test
        @DisplayName("timeout de rede no polling é repetido")
        void shouldRetryPollingOnTimeout() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willThrow(new ResourceAccessException("Read timed out"))
                    .willReturn(List.of());

            syncService.syncOrders();

            then(orderClient).should(times(2)).pollEvents("token-1", List.of("ifood-m1"));
        }

        @Test
        @DisplayName("4xx nunca é repetido")
        void shouldNotRetryOnClientError() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1"))).willThrow(badRequest());

            assertThatThrownBy(() -> syncService.syncOrders())
                    .isInstanceOf(HttpClientErrorException.class);

            then(orderClient).should().pollEvents("token-1", List.of("ifood-m1"));
            assertThat(backoffPauses).isEmpty();
        }

        @Test
        @DisplayName("5xx no detalhe do pedido é repetido antes de importar")
        void shouldRetryOrderDetailOnServerError() {
            IfoodOrderDetailResponse detail = detail("ord-1");
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "CONFIRMED", "ord-1")));
            given(orderClient.getOrderDetail("token-1", "ord-1"))
                    .willThrow(serverError())
                    .willReturn(raw(detail));

            syncService.syncOrders();

            then(orderClient).should(times(2)).getOrderDetail("token-1", "ord-1");
            then(importService).should().importOrder(detail, OrderStatus.PENDING, rawOf(detail));
        }

        @Test
        @DisplayName("5xx no acknowledgment é repetido")
        void shouldRetryAcknowledgmentOnServerError() {
            given(orderClient.pollEvents("token-1", List.of("ifood-m1")))
                    .willReturn(List.of(event("evt-1", "PLACED", "ord-1")));
            willThrow(serverError()).willDoNothing()
                    .given(orderClient).acknowledgeEvents("token-1", List.of("evt-1"));

            syncService.syncOrders();

            then(orderClient).should(times(2)).acknowledgeEvents("token-1", List.of("evt-1"));
        }
    }
}
