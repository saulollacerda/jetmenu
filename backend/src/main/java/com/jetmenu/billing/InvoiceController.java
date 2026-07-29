package com.jetmenu.billing;

import com.jetmenu.auth.AuthHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final SubscriptionService subscriptionService;
    private final AuthHelper authHelper;

    public InvoiceController(SubscriptionService subscriptionService, AuthHelper authHelper) {
        this.subscriptionService = subscriptionService;
        this.authHelper = authHelper;
    }

    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getMyInvoices(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(subscriptionService.getMyInvoices(merchantId));
    }
}
