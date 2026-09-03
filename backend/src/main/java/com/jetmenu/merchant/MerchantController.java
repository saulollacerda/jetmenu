package com.jetmenu.merchant;

import com.jetmenu.auth.AuthHelper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;
    private final AuthHelper authHelper;

    public MerchantController(MerchantService merchantService, AuthHelper authHelper) {
        this.merchantService = merchantService;
        this.authHelper = authHelper;
    }

    @PostMapping
    public ResponseEntity<MerchantResponse> create(@Valid @RequestBody MerchantRequest request) {
        MerchantResponse response = merchantService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MerchantResponse> findById(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        MerchantResponse response = merchantService.findById(merchantId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MerchantResponse> update(Authentication auth, @PathVariable UUID id, @Valid @RequestBody MerchantRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        MerchantResponse response = merchantService.update(merchantId, id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(Authentication auth, @PathVariable UUID id) {
        UUID merchantId = authHelper.getMerchantId(auth);
        merchantService.delete(merchantId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/anota-ai-key")
    public ResponseEntity<MerchantResponse> updateAnotaAIKey(Authentication auth, @RequestBody AnotaAIKeyRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        MerchantResponse response = merchantService.updateAnotaAIKey(merchantId, request);
        return ResponseEntity.ok(response);
    }

    /**
     * Os dois valores que o lojista cola no painel da Anota.AI. Autenticado como qualquer
     * outro {@code /me} — o segredo volta em texto porque o cadastro lá é manual e ele
     * precisa poder recopiá-lo.
     */
    @GetMapping("/me/anotaai-webhook")
    public ResponseEntity<AnotaAIWebhookConfigResponse> getAnotaAIWebhookConfig(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getAnotaAiWebhookConfig(merchantId));
    }

    /**
     * Gera um segredo novo e invalida o anterior. POST, não PUT: cada chamada produz um valor
     * diferente, então repetir a mesma requisição não deixa o servidor no mesmo estado.
     */
    @PostMapping("/me/anotaai-webhook/rotate")
    public ResponseEntity<AnotaAIWebhookConfigResponse> rotateAnotaAIWebhookSecret(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.rotateAnotaAiWebhookSecret(merchantId));
    }

    @GetMapping("/me")
    public ResponseEntity<MerchantResponse> findMe(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.findMe(merchantId));
    }

    @PutMapping("/me")
    public ResponseEntity<MerchantResponse> updateMe(Authentication auth, @Valid @RequestBody MerchantUpdateRequest request) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.updateMe(merchantId, request));
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<MerchantPreferences> getMyPreferences(Authentication auth) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.getMyPreferences(merchantId));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<MerchantPreferences> updateMyPreferences(Authentication auth, @RequestBody MerchantPreferences preferences) {
        UUID merchantId = authHelper.getMerchantId(auth);
        return ResponseEntity.ok(merchantService.updateMyPreferences(merchantId, preferences));
    }
}
