package com.jetmenu.order;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final AuthHelper authHelper;

    public OrderController(OrderService orderService, AuthHelper authHelper) {
        this.orderService = orderService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(Authentication auth, @Valid @RequestBody OrderRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        OrderResponse response = orderService.create(merchantId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        OrderResponse response = orderService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> findAll(
            Authentication auth,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) OrderStatus status,
            @PageableDefault(size = 20, sort = "dateTime", direction = Sort.Direction.DESC) Pageable pageable) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(orderService.findAll(merchantId, search, status, pageable));
    }

    @GetMapping("/status-counts")
    public ResponseEntity<Map<OrderStatus, Long>> statusCounts(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false, defaultValue = "") String search) {
        UUID merchantId = authHelper.getMerchantId(auth);
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;
        return ResponseEntity.ok(orderService.statusCounts(merchantId, start, end, search));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OrderResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        OrderResponse response = orderService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        orderService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }
}
