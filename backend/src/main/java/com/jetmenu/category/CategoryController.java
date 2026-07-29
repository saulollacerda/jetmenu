package com.jetmenu.category;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;
    private final AuthHelper authHelper;

    public CategoryController(CategoryService categoryService, AuthHelper authHelper) {
        this.categoryService = categoryService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(Authentication auth, @Valid @RequestBody CategoryRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CategoryResponse response = categoryService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CategoryResponse response = categoryService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<CategoryResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(categoryService.findAll(merchantId, search, pageable));
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<CategoryRevenueResponse>> revenue(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(categoryService.revenue(merchantId, startDate, endDate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        CategoryResponse response = categoryService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        categoryService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
