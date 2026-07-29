package com.jetmenu.product;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/includes")
public class IncludeController {

    private final IncludeService includeService;
    private final AuthHelper authHelper;

    public IncludeController(IncludeService includeService, AuthHelper authHelper) {
        this.includeService = includeService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<IncludeResponse> add(
            Authentication auth,
            @PathVariable UUID productId,
            @Valid @RequestBody IncludeRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        IncludeResponse response = includeService.add(merchantId, productId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/batch")
    public ResponseEntity<List<IncludeResponse>> addBatch(
            Authentication auth,
            @PathVariable UUID productId,
            @RequestBody List<@Valid IncludeRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID merchantId = authHelper.getMerchantId(auth);
        List<IncludeResponse> response = includeService.addBatch(merchantId, productId, requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<IncludeResponse>> findByProductId(Authentication auth, @PathVariable UUID productId) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(includeService.findByProductId(merchantId, productId));
    }

    @PutMapping("/{includeId}")
    public ResponseEntity<IncludeResponse> update(
            Authentication auth,
            @PathVariable UUID productId,
            @PathVariable UUID includeId,
            @Valid @RequestBody IncludeRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(includeService.update(merchantId, productId, includeId, request));
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<IncludeResponse>> reorder(
            Authentication auth,
            @PathVariable UUID productId,
            @RequestBody List<@Valid IncludeReorderRequest> items) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(includeService.reorder(merchantId, productId, items));
    }

    @Transactional
    @DeleteMapping
    public ResponseEntity<Map<String, Long>> deleteAll(Authentication auth, @PathVariable UUID productId) {
        UUID merchantId = authHelper.getMerchantId(auth);
        long deleted = includeService.deleteAllByProductId(merchantId, productId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }

    @Transactional
    @DeleteMapping("/{includeId}")
    public ResponseEntity<Void> delete(
            Authentication auth,
            @PathVariable UUID productId,
            @PathVariable UUID includeId) {
        UUID merchantId = authHelper.getMerchantId(auth);
        includeService.delete(merchantId, productId, includeId);
        return ResponseEntity.noContent().build();
    }
}
