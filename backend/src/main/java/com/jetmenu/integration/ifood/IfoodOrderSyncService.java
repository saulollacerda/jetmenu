package com.jetmenu.integration.ifood;

import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.ifood.dto.IfoodEventResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import com.jetmenu.integration.ifood.services.IfoodOrderImportService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Orquestra o polling de eventos do iFood: busca eventos de todos os merchants
 * autorizados de uma vez (token é app-level), processa os eventos do ciclo de vida
 * (CONFIRMED importa cedo como PENDING, CONCLUDED solidifica para PAID, CANCELLED
 * cancela e tira dos ganhos, CANCELLATION_REQUESTED notifica o lojista sem mexer no
 * status) e reconhece todos os eventos recebidos — inclusive os ignorados — para não
 * acumular backlog na fila do iFood.
 *
 * <p>O {@code fullCode} é aceito com e sem o prefixo {@code ORDER_} (case-insensitive):
 * o polling real retorna eventos enxutos sem prefixo, mas os payloads de referência do
 * estilo webhook usam a forma prefixada.
 *
 * <p>Padrão de retry em 401: um {@code accessToken} pode expirar entre o
 * {@code getAccessToken()} e a chamada; nesse caso força-se o refresh via
 * {@link IfoodTokenService#handleUnauthorized()} e a chamada é repetida uma
 * única vez. Futuras integrações iFood (rastreamento, disputas) devem reusar
 * esse mesmo padrão.
 *
 * <p>Padrão de retry em falha transitória: {@code 5xx} e timeouts de rede são repetidos
 * com backoff exponencial (500ms, 1s), no máximo 3 tentativas. {@code 4xx} nunca é
 * repetido — exceto o refresh único do 401 descrito acima.
 *
 * <p>Deduplicação: todo evento já processado fica registrado em
 * {@link IfoodProcessedEvent}; eventos repetidos são descartados sem ação de negócio,
 * mas continuam sendo reconhecidos para sair da fila do iFood.
 */
@Service
public class IfoodOrderSyncService {

    private static final Logger log = LoggerFactory.getLogger(IfoodOrderSyncService.class);

    private static final String CONFIRMED = "CONFIRMED";
    private static final String CANCELLED = "CANCELLED";
    private static final String CONCLUDED = "CONCLUDED";
    /**
     * Emitido quando o cliente (ou a plataforma) pede o cancelamento e o lojista precisa
     * responder. Nome do evento <strong>não verificado</strong> contra a doc oficial do iFood —
     * validar na sandbox antes da homologação. A forma prefixada
     * ({@code ORDER_CANCELLATION_REQUESTED}) é aceita pelo mesmo normalizador dos demais.
     */
    private static final String CANCELLATION_REQUESTED = "CANCELLATION_REQUESTED";
    private static final String ORDER_PREFIX = "ORDER_";

    /** Order details expire after 7 days, so an older event id can never come back meaningfully. */
    private static final int PROCESSED_EVENT_RETENTION_DAYS = 7;

    private static final int MAX_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MILLIS = 500L;

    /** Pause between retries — replaced in tests so the suite does not actually sleep. */
    @FunctionalInterface
    interface Backoff {
        void pause(long millis) throws InterruptedException;
    }

    private final IfoodOrderClient orderClient;
    private final IfoodTokenService tokenService;
    private final MerchantRepository merchantRepository;
    private final IfoodOrderImportService importService;
    private final IfoodProcessedEventRepository processedEventRepository;

    private Backoff backoff = Thread::sleep;

    public IfoodOrderSyncService(IfoodOrderClient orderClient,
                                 IfoodTokenService tokenService,
                                 MerchantRepository merchantRepository,
                                 IfoodOrderImportService importService,
                                 IfoodProcessedEventRepository processedEventRepository) {
        this.orderClient = orderClient;
        this.tokenService = tokenService;
        this.merchantRepository = merchantRepository;
        this.importService = importService;
        this.processedEventRepository = processedEventRepository;
    }

    void setBackoff(Backoff backoff) {
        this.backoff = backoff;
    }

    public void syncOrders() {
        purgeExpiredProcessedEvents();

        List<String> ifoodMerchantIds = merchantRepository
                .findAllByIfoodMerchantIdIsNotNullAndIfoodOrderSyncEnabledTrue()
                .stream()
                .map(Merchant::getIfoodMerchantId)
                .toList();
        if (ifoodMerchantIds.isEmpty()) {
            log.info("[iFood] polling ignorado — nenhum merchant autorizado com sincronia ativa");
            return;
        }

        log.info("[iFood] polling iniciado — merchants={}", ifoodMerchantIds.size());

        String token = tokenService.getAccessToken();
        List<IfoodEventResponse> events =
                withRetry(token, t -> orderClient.pollEvents(t, ifoodMerchantIds));
        if (events.isEmpty()) {
            log.info("[iFood] polling concluído — nenhum evento novo");
            return;
        }

        log.info("[iFood] polling retornou {} eventos", events.size());

        List<String> eventIds = events.stream().map(IfoodEventResponse::getId).toList();
        Set<String> alreadyProcessed = new HashSet<>(processedEventRepository.findExistingIds(eventIds));

        List<IfoodProcessedEvent> newlyProcessed = new ArrayList<>();
        for (IfoodEventResponse event : events) {
            if (alreadyProcessed.contains(event.getId())) {
                log.info("[iFood] evento {} já processado — descartado (duplicado), mas será reconhecido",
                        event.getId());
                continue;
            }
            try {
                processEvent(token, event);
            } catch (HttpClientErrorException.NotFound e) {
                log.warn("[iFood] detalhe do pedido {} indisponível (404) — pulando", event.getOrderId());
            } catch (RuntimeException e) {
                log.error("[iFood] ERRO ao processar evento {} do pedido {}: {}",
                        event.getFullCode(), event.getOrderId(), e.getMessage(), e);
            }
            // Registrado mesmo em caso de falha: o evento é reconhecido logo abaixo e o iFood
            // não vai reenviá-lo, então reprocessá-lo depois só duplicaria efeito de negócio.
            // O id também entra no conjunto em memória — assim uma repetição dentro do próprio
            // lote de polling é descartada antes de virar um segundo processamento.
            alreadyProcessed.add(event.getId());
            newlyProcessed.add(IfoodProcessedEvent.builder()
                    .eventId(event.getId())
                    .processedAt(LocalDateTime.now())
                    .build());
        }
        if (!newlyProcessed.isEmpty()) {
            processedEventRepository.saveAll(newlyProcessed);
        }

        // Todo evento recebido é reconhecido — inclusive os duplicados e os ignorados —
        // senão o iFood reenvia o mesmo lote no próximo polling.
        withRetry(token, t -> {
            orderClient.acknowledgeEvents(t, eventIds);
            return null;
        });
    }

    private void purgeExpiredProcessedEvents() {
        int purged = processedEventRepository.deleteProcessedBefore(
                LocalDateTime.now().minusDays(PROCESSED_EVENT_RETENTION_DAYS));
        if (purged > 0) {
            log.info("[iFood] {} ids de eventos processados há mais de {} dias foram expurgados",
                    purged, PROCESSED_EVENT_RETENTION_DAYS);
        }
    }

    private void processEvent(String token, IfoodEventResponse event) {
        switch (normalizeFullCode(event.getFullCode())) {
            case CONFIRMED -> importOrder(token, event, OrderStatus.PENDING);
            case CONCLUDED -> {
                if (!importService.concludeOrder(event.getOrderId(), event.getMerchantId())) {
                    importOrder(token, event, OrderStatus.PAID);
                }
            }
            case CANCELLED -> {
                if (!importService.cancelOrder(event.getOrderId(), event.getMerchantId())) {
                    importOrder(token, event, OrderStatus.CANCELLED);
                }
            }
            case CANCELLATION_REQUESTED -> {
                // Pedido ainda não importado é trazido como PENDING primeiro: sem o pedido local
                // o lojista não teria como aceitar nem recusar a solicitação. O status NÃO muda —
                // uma solicitação sem resposta não pode tirar o pedido dos ganhos.
                if (!importService.registerCancellationRequest(event.getOrderId(), event.getMerchantId())) {
                    importOrder(token, event, OrderStatus.PENDING);
                    importService.registerCancellationRequest(event.getOrderId(), event.getMerchantId());
                }
            }
            default -> { /* apenas reconhecido, sem ação de negócio */ }
        }
    }

    private void importOrder(String token, IfoodEventResponse event, OrderStatus status) {
        RawJsonResponse<IfoodOrderDetailResponse> detail =
                withRetry(token, t -> orderClient.getOrderDetail(t, event.getOrderId()));
        if (detail == null) {
            log.warn("[iFood] detalhe do pedido {} veio vazio — pulando", event.getOrderId());
            return;
        }
        importService.importOrder(detail.body(), status, detail.rawJson());
    }

    private static String normalizeFullCode(String fullCode) {
        if (fullCode == null) {
            return "";
        }
        String normalized = fullCode.toUpperCase(Locale.ROOT);
        return normalized.startsWith(ORDER_PREFIX) ? normalized.substring(ORDER_PREFIX.length()) : normalized;
    }

    /**
     * Combina os dois padrões de retry: falhas transitórias ({@code 5xx}/timeout) são
     * repetidas com backoff, e um {@code 401} dispara o refresh único do token — que por
     * sua vez também ganha o retry transitório na repetição.
     */
    private <T> T withRetry(String token, Function<String, T> call) {
        return withRetryOn401(token, t -> withRetryOnTransientFailure(() -> call.apply(t)));
    }

    private <T> T withRetryOn401(String token, Function<String, T> call) {
        try {
            return call.apply(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("[iFood] 401 recebido — forçando refresh do token e repetindo a chamada");
            return call.apply(tokenService.handleUnauthorized());
        }
    }

    /**
     * Repete a chamada apenas em falhas transitórias — {@code 5xx} e erros de acesso à rede
     * (timeout, conexão recusada). Erros {@code 4xx} são de contrato e nunca são repetidos.
     */
    private <T> T withRetryOnTransientFailure(Supplier<T> call) {
        long delayMillis = INITIAL_BACKOFF_MILLIS;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (HttpServerErrorException | ResourceAccessException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.error("[iFood] falha transitória persistiu após {} tentativas: {}",
                            MAX_ATTEMPTS, e.getMessage());
                    throw (RestClientException) e;
                }
                log.warn("[iFood] falha transitória na tentativa {}/{} ({}) — repetindo em {}ms",
                        attempt, MAX_ATTEMPTS, e.getMessage(), delayMillis);
                pause(delayMillis);
                delayMillis *= 2;
            }
        }
    }

    private void pause(long millis) {
        try {
            backoff.pause(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("iFood retry backoff interrupted", e);
        }
    }
}
