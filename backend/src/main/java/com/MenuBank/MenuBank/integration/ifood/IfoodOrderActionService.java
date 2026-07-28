package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.integration.ifood.dto.IfoodCancellationReasonResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderActionResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderCancelRequest;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderConfirmationWindowResponse;
import com.MenuBank.MenuBank.order.Order;
import com.MenuBank.MenuBank.order.OrderOrigin;
import com.MenuBank.MenuBank.order.OrderRepository;
import com.MenuBank.MenuBank.order.OrderStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Merchant-driven iFood order lifecycle actions required by the Order module homologation:
 * {@code confirm} (within the 8-minute SLA for DELIVERY/TAKEOUT), {@code readyToPickup}
 * (TAKEOUT) and {@code dispatch} (DELIVERY with the merchant's own delivery).
 *
 * <p>Every action resolves the local {@link Order} scoped to the authenticated merchant,
 * pushes the action to iFood and only then reflects the outcome on the local status —
 * a failed call never leaves the local order out of sync:
 * <ul>
 *   <li>{@code confirm} keeps the order {@code PENDING} (it is already imported as
 *       {@code PENDING} on the CONFIRMED event);</li>
 *   <li>{@code readyToPickup} moves it to {@code READY};</li>
 *   <li>{@code dispatch} moves it to {@code DELIVERED};</li>
 *   <li>{@code cancel} and {@code acceptCancellation} move it to {@code CANCELLED};</li>
 *   <li>{@code denyCancellation} leaves the status untouched.</li>
 * </ul>
 *
 * <p>Cancellation is the second homologation criterion: the merchant picks a reason from
 * {@link #getCancellationReasons(UUID, UUID)} (iFood's own list, never one of ours), and
 * answers a customer/platform request with {@link #acceptCancellation(UUID, UUID)} or
 * {@link #denyCancellation(UUID, UUID)}. Cancelling only removes the order from the earnings
 * through the status change — there is no refund logic here.
 *
 * <p>Confirmation is never automatic: there is no scheduler here, the merchant decides.
 * {@link #getConfirmationWindow(UUID, UUID)} exposes the SLA countdown so the UI can show
 * how much of the 8-minute window is left.
 *
 * <p>Retry on 401 reuses the pattern documented in {@link IfoodOrderSyncService}: an
 * {@code accessToken} may expire between {@code getAccessToken()} and the call, so a
 * {@code 401} forces a refresh via {@link IfoodTokenService#handleUnauthorized()} and the
 * call is repeated exactly once.
 */
@Service
public class IfoodOrderActionService {

    private static final Logger log = LoggerFactory.getLogger(IfoodOrderActionService.class);

    /**
     * iFood homologation requires DELIVERY and TAKEOUT orders to be confirmed within
     * 8 minutes of creation, regardless of {@code orderTiming}.
     */
    public static final Duration CONFIRMATION_SLA = Duration.ofMinutes(8);

    private static final ZoneId BRAZIL_ZONE = ZoneId.of("America/Sao_Paulo");

    /** Statuses on which no lifecycle action may be pushed to iFood. */
    private static final Set<OrderStatus> TERMINAL_STATUSES =
            EnumSet.of(OrderStatus.CANCELLED, OrderStatus.TEST);

    private final IfoodOrderActionClient actionClient;
    private final IfoodTokenService tokenService;
    private final OrderRepository orderRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IfoodOrderActionService(IfoodOrderActionClient actionClient,
                                   IfoodTokenService tokenService,
                                   OrderRepository orderRepository) {
        this.actionClient = actionClient;
        this.tokenService = tokenService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public IfoodOrderActionResponse confirm(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.confirm(token, order.getExternalOrderId()));
        // No local transition: the order was already imported as PENDING on the CONFIRMED event.
        return toResponse(order);
    }

    @Transactional
    public IfoodOrderActionResponse readyToPickup(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.readyToPickup(token, order.getExternalOrderId()));
        return applyStatus(order, OrderStatus.READY);
    }

    @Transactional
    public IfoodOrderActionResponse dispatch(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.dispatch(token, order.getExternalOrderId()));
        return applyStatus(order, OrderStatus.DELIVERED);
    }

    /**
     * Cancellation reasons iFood accepts for this order. Homologation requires the merchant
     * to choose from iFood's own list, so the codes and descriptions are surfaced verbatim.
     */
    @Transactional(readOnly = true)
    public List<IfoodCancellationReasonResponse> getCancellationReasons(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        return executeForResult(token -> actionClient.cancellationReasons(token, order.getExternalOrderId()));
    }

    /**
     * Merchant-initiated cancellation. The local order only moves to {@code CANCELLED} after
     * iFood accepts the request — which is what removes it from the earnings (dashboard and
     * export aggregate {@code PAID} only). There is no refund logic: the status change is the
     * whole effect.
     */
    @Transactional
    public IfoodOrderActionResponse cancel(UUID merchantId, UUID orderId, IfoodOrderCancelRequest request) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.requestCancellation(
                token, order.getExternalOrderId(), request.cancellationCode(), request.reason()));
        log.info("[iFood] cancelamento do pedido {} solicitado pelo lojista — motivo '{}'",
                order.getExternalOrderId(), request.cancellationCode());
        return applyStatus(order, OrderStatus.CANCELLED);
    }

    /**
     * Accepts a cancellation requested by the customer or by the platform: iFood settles the
     * cancellation and the order is then cancelled locally.
     */
    @Transactional
    public IfoodOrderActionResponse acceptCancellation(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.acceptCancellation(token, order.getExternalOrderId()));
        log.info("[iFood] solicitação de cancelamento do pedido {} aceita pelo lojista",
                order.getExternalOrderId());
        return applyStatus(order, OrderStatus.CANCELLED);
    }

    /**
     * Rejects a cancellation requested by the customer or by the platform. The order keeps
     * its local status — the request never touched it in the first place.
     */
    @Transactional
    public IfoodOrderActionResponse denyCancellation(UUID merchantId, UUID orderId) {
        Order order = requireActionableOrder(merchantId, orderId);
        execute(token -> actionClient.denyCancellation(token, order.getExternalOrderId()));
        log.info("[iFood] solicitação de cancelamento do pedido {} recusada pelo lojista",
                order.getExternalOrderId());
        return toResponse(order);
    }

    /**
     * Confirmation SLA countdown of an iFood order — {@code deadline = dateTime + 8 min},
     * with {@code remainingSeconds} clamped at zero once the window is gone.
     */
    @Transactional(readOnly = true)
    public IfoodOrderConfirmationWindowResponse getConfirmationWindow(UUID merchantId, UUID orderId) {
        Order order = requireIfoodOrder(merchantId, orderId);
        LocalDateTime createdAt = order.getDateTime();
        LocalDateTime deadline = createdAt.plus(CONFIRMATION_SLA);
        long remainingSeconds = Math.max(0L,
                Duration.between(LocalDateTime.now(BRAZIL_ZONE), deadline).toSeconds());
        return new IfoodOrderConfirmationWindowResponse(
                order.getId(), createdAt, deadline, remainingSeconds, remainingSeconds == 0L);
    }

    private IfoodOrderActionResponse applyStatus(Order order, OrderStatus status) {
        order.setStatus(status);
        orderRepository.save(order);
        return toResponse(order);
    }

    private IfoodOrderActionResponse toResponse(Order order) {
        return new IfoodOrderActionResponse(order.getId(), order.getExternalOrderId(), order.getStatus());
    }

    /** Loads an iFood order of the merchant, rejecting orders no action may target. */
    private Order requireActionableOrder(UUID merchantId, UUID orderId) {
        Order order = requireIfoodOrder(merchantId, orderId);
        if (TERMINAL_STATUSES.contains(order.getStatus())) {
            throw new IfoodOrderActionNotAllowedException(
                    IfoodOrderActionNotAllowedException.Reason.TERMINAL_STATUS);
        }
        return order;
    }

    private Order requireIfoodOrder(UUID merchantId, UUID orderId) {
        Order order = orderRepository.findByIdAndMerchantId(orderId, merchantId)
                .orElseThrow(() -> new IfoodOrderNotFoundException(orderId));
        if (order.getOrigin() != OrderOrigin.IFOOD) {
            throw new IfoodOrderActionNotAllowedException(
                    IfoodOrderActionNotAllowedException.Reason.NOT_IFOOD_ORDER);
        }
        if (order.getExternalOrderId() == null || order.getExternalOrderId().isBlank()) {
            throw new IfoodOrderActionNotAllowedException(
                    IfoodOrderActionNotAllowedException.Reason.MISSING_EXTERNAL_ID);
        }
        return order;
    }

    private void execute(Consumer<String> call) {
        executeForResult(token -> {
            call.accept(token);
            return null;
        });
    }

    /**
     * Same as {@link #execute(Consumer)} for calls that read something back from iFood.
     * Distinct name on purpose: overloading on {@code Consumer}/{@code Function} makes every
     * lambda call site ambiguous.
     */
    private <T> T executeForResult(Function<String, T> call) {
        try {
            return withRetryOn401(call);
        } catch (HttpClientErrorException e) {
            throw translate(e);
        }
    }

    private <T> T withRetryOn401(Function<String, T> call) {
        String token = tokenService.getAccessToken();
        try {
            return call.apply(token);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("[iFood] 401 recebido — forçando refresh do token e repetindo a ação do pedido");
            return call.apply(tokenService.handleUnauthorized());
        }
    }

    private RuntimeException translate(HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            case 401 -> new IfoodReauthorizationRequiredException();
            case 404 -> new IfoodResourceNotFoundException();
            default -> new IfoodBadRequestException(extractDetail(e));
        };
    }

    private String extractDetail(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getStatusText();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : List.of("message", "error", "detail")) {
                JsonNode node = root.path(field);
                if (node.isTextual() && !node.asText().isBlank()) {
                    return node.asText();
                }
            }
        } catch (Exception ignored) {
            // Body is not JSON — fall back to the raw payload below.
        }
        return body;
    }
}
