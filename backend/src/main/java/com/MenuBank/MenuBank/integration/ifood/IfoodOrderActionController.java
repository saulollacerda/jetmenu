package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.auth.AuthHelper;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderActionResponse;
import com.MenuBank.MenuBank.integration.ifood.dto.IfoodOrderConfirmationWindowResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Merchant-driven iFood order lifecycle actions required by the Order module homologation.
 * The three actions are modelled as POST sub-resources of the local order because they are
 * commands pushed to iFood, not updates of a MenuBank resource; each returns the local
 * order status resulting from the action.
 *
 * <p>{@code GET /confirmation-window} supports the 8-minute confirmation SLA countdown in
 * the UI. Confirmation itself is always a merchant decision — nothing here auto-confirms.
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
