package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import com.jetmenu.merchant.DuplicateMerchantException;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantNotFoundException;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.MerchantResponse;
import com.jetmenu.merchant.MerchantStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Just-in-time provisioning: links a Supabase user (provider_user_id) to a JetMenu
 * merchant, creating the merchant on first access. Idempotent — a second call for an
 * already-provisioned user returns the existing merchant.
 */
@Service
public class ProvisionService {

    @Value("${app.auth.provider:supabase}")
    private String provider = "supabase";

    private final MerchantRepository merchantRepository;
    private final IdentityRepository identityRepository;

    public ProvisionService(MerchantRepository merchantRepository,
                            IdentityRepository identityRepository) {
        this.merchantRepository = merchantRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public MerchantResponse provision(String providerUserId, ProvisionRequest request) {
        var existing = identityRepository.findByProviderAndProviderUserId(provider, providerUserId);
        if (existing.isPresent()) {
            Merchant merchant = merchantRepository.findById(existing.get().getMerchantId())
                    .orElseThrow(() -> new MerchantNotFoundException(existing.get().getMerchantId()));
            return toResponse(merchant);
        }

        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateMerchantException("email");
        }
        if (merchantRepository.existsByCnpj(request.getCnpj())) {
            throw new DuplicateMerchantException("CNPJ");
        }

        LocalDateTime now = LocalDateTime.now();
        Merchant merchant = Merchant.builder()
                .merchantName(request.getMerchantName())
                .cnpj(request.getCnpj())
                .email(request.getEmail())
                .phone(request.getPhone())
                .status(MerchantStatus.ACTIVE)
                .createdAt(now)
                .build();
        Merchant saved = merchantRepository.save(merchant);

        identityRepository.save(Identity.builder()
                .merchantId(saved.getId())
                .provider(provider)
                .providerUserId(providerUserId)
                .createdAt(now)
                .build());

        return toResponse(saved);
    }

    private MerchantResponse toResponse(Merchant merchant) {
        return MerchantResponse.builder()
                .id(merchant.getId())
                .merchantName(merchant.getMerchantName())
                .cnpj(merchant.getCnpj())
                .email(merchant.getEmail())
                .phone(merchant.getPhone())
                .status(merchant.getStatus())
                .createdAt(merchant.getCreatedAt())
                .anotaAiApiKey(merchant.getAnotaAiApiKey())
                .address(merchant.getAddress())
                .logoUrl(merchant.getLogoUrl())
                .openingHours(merchant.getOpeningHours())
                .preferences(merchant.getPreferences())
                .build();
    }
}
