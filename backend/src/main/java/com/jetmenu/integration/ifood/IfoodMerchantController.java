package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionRequest;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursRequest;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read/write passthrough to the iFood Merchant module — store details, status,
 * interruptions (pauses) and opening hours. JetMenu owns none of this data: every
 * endpoint proxies iFood at request time. All error responses are {@link ProblemDetail}
 * with pt-BR details.
 */
@RestController
@RequestMapping("/api/integrations/ifood/merchant")
public class IfoodMerchantController {

    private final IfoodMerchantService merchantService;
    private final AuthHelper authHelper;

    public IfoodMerchantController(IfoodMerchantService merchantService, AuthHelper authHelper) {
        this.merchantService = merchantService;
        this.authHelper = authHelper;
    }

    @GetMapping("/details")
    public ResponseEntity<IfoodMerchantDetailsResponse> getDetails(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getDetails(merchantId));
    }

    @GetMapping("/status")
    public ResponseEntity<List<IfoodMerchantStatusResponse>> getStatus(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getStatus(merchantId));
    }

    @GetMapping("/interruptions")
    public ResponseEntity<List<IfoodInterruptionResponse>> getInterruptions(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getInterruptions(merchantId));
    }

    @PostMapping("/interruptions")
    public ResponseEntity<IfoodInterruptionResponse> createInterruption(
            @RequestBody IfoodInterruptionRequest request, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.createInterruption(merchantId, request));
    }

    @DeleteMapping("/interruptions/{interruptionId}")
    public ResponseEntity<Void> deleteInterruption(
            @PathVariable String interruptionId, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        merchantService.deleteInterruption(merchantId, interruptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/opening-hours")
    public ResponseEntity<IfoodOpeningHoursResponse> getOpeningHours(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getOpeningHours(merchantId));
    }

    @PutMapping("/opening-hours")
    public ResponseEntity<IfoodOpeningHoursResponse> updateOpeningHours(
            @RequestBody IfoodOpeningHoursRequest request, Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.updateOpeningHours(merchantId, request));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ProblemDetail> handleNotConnected(IllegalStateException ex) {
        return problem(HttpStatus.CONFLICT,
                "Conecte sua conta do iFood para gerenciar sua loja.");
    }

    @ExceptionHandler(IfoodReauthorizationRequiredException.class)
    public ResponseEntity<ProblemDetail> handleReauthorizationRequired(
            IfoodReauthorizationRequiredException ex) {
        return problem(HttpStatus.CONFLICT,
                "A autorização com o iFood expirou. Reconecte sua conta e tente novamente.");
    }

    @ExceptionHandler(IfoodInterruptionOverlapException.class)
    public ResponseEntity<ProblemDetail> handleInterruptionOverlap(IfoodInterruptionOverlapException ex) {
        return problem(HttpStatus.CONFLICT,
                "Já existe uma pausa nesse período. Remova a pausa existente ou escolha outro horário.");
    }

    @ExceptionHandler(IfoodShiftOverlapException.class)
    public ResponseEntity<ProblemDetail> handleShiftOverlap(IfoodShiftOverlapException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Os turnos informados se sobrepõem.");
    }

    @ExceptionHandler(IfoodBadRequestException.class)
    public ResponseEntity<ProblemDetail> handleBadRequest(IfoodBadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Dados inválidos: " + ex.getDetail());
    }

    @ExceptionHandler(IfoodResourceNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(IfoodResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Recurso não encontrado no iFood.");
    }

    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail) {
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, detail));
    }
}
