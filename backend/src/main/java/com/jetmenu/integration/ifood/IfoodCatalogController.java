package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusChange;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusSyncRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult;
import com.jetmenu.integration.ifood.services.IfoodCatalogImportService;
import com.jetmenu.integration.ifood.services.IfoodCatalogPublishService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo do iFood nos dois sentidos: importação (iFood → JetMenu) e publicação
 * (JetMenu → iFood, contexto WHITELABEL). Erros do iFood chegam aqui como exceções
 * tipadas e saem como {@link ProblemDetail} com mensagem em pt-BR — nada falha em silêncio.
 */
@RestController
@RequestMapping("/api/integrations/ifood/catalog")
public class IfoodCatalogController {

    private final IfoodCatalogImportService importService;
    private final IfoodCatalogPublishService publishService;
    private final AuthHelper authHelper;

    public IfoodCatalogController(IfoodCatalogImportService importService,
                                  IfoodCatalogPublishService publishService,
                                  AuthHelper authHelper) {
        this.importService = importService;
        this.publishService = publishService;
        this.authHelper = authHelper;
    }

    @PostMapping("/import")
    public ResponseEntity<IfoodCatalogImportResult> importCatalog(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(importService.importCatalog(merchantId));
    }

    /** Corpo opcional: sem {@code productIds} publica todos os produtos ativos. */
    @PostMapping("/publish")
    public ResponseEntity<IfoodCatalogPublishResult> publish(
            @RequestBody(required = false) IfoodCatalogPublishRequest request,
            Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(publishService.publish(merchantId, productIds(request)));
    }

    @PatchMapping("/prices")
    public ResponseEntity<IfoodCatalogSyncResult> syncPrices(
            @RequestBody(required = false) IfoodCatalogPublishRequest request,
            Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(publishService.syncPrices(merchantId, productIds(request)));
    }

    @PatchMapping("/status")
    public ResponseEntity<IfoodCatalogSyncResult> syncStatus(
            @RequestBody(required = false) IfoodCatalogStatusSyncRequest request,
            Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        List<IfoodCatalogStatusChange> items =
                request != null && request.items() != null ? request.items() : List.of();
        return ResponseEntity.ok(publishService.syncStatus(merchantId, items));
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<IfoodCatalogBatchResponse> getBatch(@PathVariable String batchId,
                                                              Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(publishService.getBatch(merchantId, batchId));
    }

    private static List<UUID> productIds(IfoodCatalogPublishRequest request) {
        return request != null ? request.productIds() : null;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleNotConnected(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT,
                "Conecte sua conta do iFood antes de sincronizar o catálogo.");
    }

    @ExceptionHandler(IfoodReauthorizationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleReauthorizationRequired(
            IfoodReauthorizationRequiredException ex) {
        return problem(HttpStatus.CONFLICT,
                "A autorização com o iFood expirou. Reconecte sua conta e tente novamente.");
    }

    /** {@code 400}/{@code VALIDATION_ERROR} do iFood → {@code 422}. */
    @ExceptionHandler(IfoodBadRequestException.class)
    public ResponseEntity<ProblemDetail> handleValidationError(IfoodBadRequestException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY,
                "O iFood recusou os dados do catálogo: " + ex.getDetail());
    }

    @ExceptionHandler(IfoodResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(IfoodResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado no iFood.");
    }

    @ExceptionHandler(IfoodCatalogConflictException.class)
    public ResponseEntity<ProblemDetail> handleConflict(IfoodCatalogConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Conflito no catálogo do iFood: " + ex.getDetail());
    }

    /** {@code 5xx} irrecuperável (retries esgotados) → {@code 503}. */
    @ExceptionHandler(IfoodUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleUnavailable(IfoodUnavailableException ex) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE,
                "O iFood está indisponível no momento. Tente novamente em alguns instantes.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
