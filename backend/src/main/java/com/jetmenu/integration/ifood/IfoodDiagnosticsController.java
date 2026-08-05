package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import com.jetmenu.integration.ifood.services.IfoodDiagnosticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Diagnóstico read-only do catálogo do iFood, usado nas reuniões de homologação: cada
 * endpoint aqui corresponde a um item do checklist e devolve a resposta crua do iFood
 * (URL chamada, status HTTP e corpo), sem tocar em nada do JetMenu.
 *
 * <p>Liberado apenas para os merchants em {@code ifood.diagnostics-merchant-ids}. Quem não
 * está na lista recebe {@code 403} — a UI consulta {@code /access} antes para nem oferecer
 * a tela.</p>
 */
@RestController
@RequestMapping("/api/integrations/ifood/diagnostics")
public class IfoodDiagnosticsController {

    private final IfoodDiagnosticsService diagnosticsService;
    private final AuthHelper authHelper;

    public IfoodDiagnosticsController(IfoodDiagnosticsService diagnosticsService,
                                      AuthHelper authHelper) {
        this.diagnosticsService = diagnosticsService;
        this.authHelper = authHelper;
    }

    /** Sempre {@code 200}: não estar liberado é uma resposta válida, não um erro. */
    @GetMapping("/access")
    public ResponseEntity<Map<String, Boolean>> access(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(Map.of("enabled", diagnosticsService.isEnabledFor(merchantId)));
    }

    @GetMapping("/catalogs")
    public ResponseEntity<IfoodRawResponse> listCatalogs(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(diagnosticsService.listCatalogs(merchantId));
    }

    /** Sem {@code catalogId}, o serviço resolve o catálogo DEFAULT da loja. */
    @GetMapping("/items")
    public ResponseEntity<IfoodRawResponse> listItems(
            @RequestParam(required = false) String catalogId,
            Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(diagnosticsService.listItems(merchantId, catalogId));
    }

    @ExceptionHandler(IfoodDiagnosticsNotAllowedException.class)
    public ResponseEntity<ProblemDetail> handleNotAllowed(IfoodDiagnosticsNotAllowedException ex) {
        return problem(HttpStatus.FORBIDDEN,
                "O diagnóstico do iFood não está liberado para esta loja.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleNotConnected(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT,
                "Conecte sua conta do iFood antes de usar o diagnóstico.");
    }

    @ExceptionHandler(IfoodReauthorizationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleReauthorizationRequired(
            IfoodReauthorizationRequiredException ex) {
        return problem(HttpStatus.CONFLICT,
                "A autorização com o iFood expirou. Reconecte sua conta e tente novamente.");
    }

    @ExceptionHandler(IfoodResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(IfoodResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND,
                "Nenhum catálogo encontrado no iFood para esta loja.");
    }

    @ExceptionHandler(IfoodUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUnavailable(IfoodUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "O iFood está indisponível no momento. Tente novamente em alguns instantes.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
