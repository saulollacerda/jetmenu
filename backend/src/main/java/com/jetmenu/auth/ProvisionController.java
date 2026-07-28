package com.jetmenu.auth;

import com.jetmenu.merchant.MerchantResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class ProvisionController {

    private final ProvisionService provisionService;

    public ProvisionController(ProvisionService provisionService) {
        this.provisionService = provisionService;
    }

    @PostMapping("/provision")
    public ResponseEntity<MerchantResponse> provision(@Valid @RequestBody ProvisionRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String providerUserId = auth.getName();
        MerchantResponse response = provisionService.provision(providerUserId, request);
        return ResponseEntity.ok(response);
    }
}
