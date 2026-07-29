package com.jetmenu.customer;

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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AuthHelper authHelper;

    public CustomerController(CustomerService customerService, AuthHelper authHelper) {
        this.customerService = customerService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(Authentication auth, @Valid @RequestBody CustomerRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CustomerResponse response = customerService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CustomerResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CustomerResponse response = customerService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<CustomerResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(customerService.findAll(merchantId, search, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CustomerResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody CustomerRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CustomerResponse response = customerService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        customerService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
