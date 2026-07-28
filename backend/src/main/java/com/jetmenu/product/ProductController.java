package com.jetmenu.product;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final AuthHelper authHelper;

    public ProductController(ProductService productService, AuthHelper authHelper) {
        this.productService = productService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(Authentication auth, @Valid @RequestBody ProductRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        ProductResponse response = productService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        ProductResponse response = productService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(productService.findAll(merchantId, search, pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        ProductResponse response = productService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        productService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
