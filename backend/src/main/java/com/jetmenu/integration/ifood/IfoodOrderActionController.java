package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodCancellationReasonResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderActionResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderCancelRequest;
import com.jetmenu.integration.ifood.dto.IfoodOrderConfirmationWindowResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Merchant-driven iFood order lifecycle actions required by the Order module homologation.
 * Each action is modelled as a POST sub-resource of the local order because they are
 * commands pushed to iFood, not updates of a JetMenu resource; each returns the local
 * order status resulting from the action.
 *
 * <p>{@code GET /confirmation-window} supports the 8-minute confirmation SLA countdown in
 * the UI. Confirmation itself is always a merchant decision — nothing here auto-confirms.
 *
 * <p>Cancellation spans three endpoints: {@code GET /cancellation-reasons} feeds the picker
 * with iFood's own reasons, {@code POST /cancel} cancels on the merchant's initiative, and
 * {@code POST /cancellation-request/{accept,deny}} answers a request raised by the customer
 * or by the platform.
 *
 * <p>All error responses are {@link ProblemDetail} with pt-BR details.
 */
@RestController
@RequestMapping("/api/integrations/ifood/orders")
public class IfoodOrderActionController {

    private final IfoodOrderActionService actionService;
    private final AuthHelper authHelper;

    public IfoodOrderActionController(IfoodOrderActionService actionService, AuthHelper authHelper) {
        this.actionService = actionService;
        this.authHelper = authHelper;
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<IfoodOrderActionResponse> confirm(@PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.confirm(merchantId, orderId));
    }

    @PostMapping("/{orderId}/ready-to-pickup")
    public ResponseEntity<IfoodOrderActionResponse> readyToPickup(@PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.readyToPickup(merchantId, orderId));
    }

    @PostMapping("/{orderId}/dispatch")
    public ResponseEntity<IfoodOrderActionResponse> dispatch(@PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.dispatch(merchantId, orderId));
    }

    /**
     * Cancellation reasons offered by iFood for this order. The merchant must pick one of
     * these — JetMenu never invents a reason.
     */
    @GetMapping("/{orderId}/cancellation-reasons")
    public ResponseEntity<List<IfoodCancellationReasonResponse>> cancellationReasons(
            @PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.getCancellationReasons(merchantId, orderId));
    }

    /** Merchant-initiated cancellation, carrying the {@code cancelCodeId} the merchant chose. */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<IfoodOrderActionResponse> cancel(
            @PathVariable UUID orderId,
            @Valid @RequestBody IfoodOrderCancelRequest request,
            Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.cancel(merchantId, orderId, request));
    }

    /** Accepts a cancellation requested by the customer or by the platform. */
    @PostMapping("/{orderId}/cancellation-request/accept")
    public ResponseEntity<IfoodOrderActionResponse> acceptCancellation(
            @PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.acceptCancellation(merchantId, orderId));
    }

    /** Rejects a cancellation requested by the customer or by the platform. */
    @PostMapping("/{orderId}/cancellation-request/deny")
    public ResponseEntity<IfoodOrderActionResponse> denyCancellation(
            @PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.denyCancellation(merchantId, orderId));
    }

    @GetMapping("/{orderId}/confirmation-window")
    public ResponseEntity<IfoodOrderConfirmationWindowResponse> getConfirmationWindow(
            @PathVariable UUID orderId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(actionService.getConfirmationWindow(merchantId, orderId));
    }

    @ExceptionHandler(IfoodOrderNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleOrderNotFound(IfoodOrderNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Pedido não encontrado.");
    }

    @ExceptionHandler(IfoodOrderActionNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleActionNotAllowed(IfoodOrderActionNotAllowedException ex) {
        return problem(HttpStatus.CONFLICT, switch (ex.getReason()) {
            case NOT_IFOOD_ORDER -> "Esta ação só está disponível para pedidos do iFood.";
            case MISSING_EXTERNAL_ID ->
                    "Este pedido não tem identificador do iFood, então não é possível atualizá-lo.";
            case TERMINAL_STATUS -> "Pedido cancelado ou de teste não pode ser atualizado no iFood.";
            case NOT_A_TAKEOUT_ORDER ->
                    "Só é possível marcar como pronto para retirada um pedido de retirada.";
            case NOT_A_DELIVERY_ORDER -> "Só é possível despachar um pedido de entrega.";
        });
    }

    @ExceptionHandler(IfoodResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFoundOnIfood(IfoodResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Pedido não encontrado no iFood.");
    }

    @ExceptionHandler(IfoodBadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IfoodBadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "O iFood recusou a ação: " + ex.getDetail());
    }

    @ExceptionHandler(IfoodReauthorizationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleReauthorizationRequired(
            IfoodReauthorizationRequiredException ex) {
        return problem(HttpStatus.CONFLICT,
                "A autorização com o iFood expirou. Reconecte sua conta e tente novamente.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
