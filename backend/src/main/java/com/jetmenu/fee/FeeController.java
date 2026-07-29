package com.jetmenu.fee;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/fees")
public class FeeController {

    private final FeeService feeService;
    private final AuthHelper authHelper;

    public FeeController(FeeService feeService, AuthHelper authHelper) {
        this.feeService = feeService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<FeeResponse> create(Authentication auth, @Valid @RequestBody FeeRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        FeeResponse response = feeService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeeResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        FeeResponse response = feeService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<FeeResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(feeService.findAll(merchantId, search, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FeeResponse> update(Authentication auth, @PathVariable UUID id,
                                              @Valid @RequestBody FeeRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        FeeResponse response = feeService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        feeService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
