package com.jetmenu.merchant;

import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.common.ForbiddenException;
import com.jetmenu.config.CacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;

    public MerchantService(MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder,
                           SubscriptionService subscriptionService) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
    }

    public MerchantResponse create(MerchantRequest request) {
        if (merchantRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateMerchantException("email");
        }
        if (merchantRepository.existsByCnpj(request.getCnpj())) {
            throw new DuplicateMerchantException("CNPJ");
        }

        Merchant merchant = Merchant.builder()
                .merchantName(request.getMerchantName())
                .cnpj(request.getCnpj())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        Merchant saved = merchantRepository.save(merchant);
        subscriptionService.createPendingSubscription(saved.getId());
        return toResponse(saved);
    }

    public MerchantResponse findById(UUID currentMerchantId, UUID id) {
        ensureOwner(currentMerchantId, id);
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));
        return toResponse(merchant);
    }

    @CacheEvict(value = CacheConfig.MERCHANT_ID_BY_PROVIDER_USER, allEntries = true)
    public MerchantResponse update(UUID currentMerchantId, UUID id, MerchantRequest request) {
        ensureOwner(currentMerchantId, id);
        Merchant merchant = merchantRepository.findById(id)
                .orElseThrow(() -> new MerchantNotFoundException(id));

        merchant.setMerchantName(request.getMerchantName());
        merchant.setCnpj(request.getCnpj());
        merchant.setEmail(request.getEmail());
        merchant.setPassword(passwordEncoder.encode(request.getPassword()));
        merchant.setPhone(request.getPhone());
        merchant.setAnotaAiApiKey(request.getAnotaAiApiKey());

        Merchant saved = merchantRepository.save(merchant);
        return toResponse(saved);
    }

    @CacheEvict(value = CacheConfig.MERCHANT_ID_BY_PROVIDER_USER, allEntries = true)
    public MerchantResponse updateAnotaAIKey(UUID currentMerchantId, AnotaAIKeyRequest request) {
        Merchant merchant = merchantRepository.findById(currentMerchantId)
                .orElseThrow(() -> new MerchantNotFoundException(currentMerchantId));

        merchant.setAnotaAiApiKey(request.getAnotaAiApiKey());
        Merchant saved = merchantRepository.save(merchant);
        return toResponse(saved);
    }

    public MerchantResponse findMe(UUID currentMerchantId) {
        Merchant merchant = merchantRepository.findById(currentMerchantId)
                .orElseThrow(() -> new MerchantNotFoundException(currentMerchantId));
        return toResponse(merchant);
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MERCHANT_ID_BY_PROVIDER_USER, allEntries = true)
    public MerchantResponse updateMe(UUID currentMerchantId, MerchantUpdateRequest request) {
        Merchant merchant = merchantRepository.findById(currentMerchantId)
                .orElseThrow(() -> new MerchantNotFoundException(currentMerchantId));

        if (request.getMerchantName() != null) merchant.setMerchantName(request.getMerchantName());
        if (request.getPhone() != null) merchant.setPhone(request.getPhone());
        if (request.getAddress() != null) merchant.setAddress(request.getAddress());
        if (request.getLogoUrl() != null) merchant.setLogoUrl(request.getLogoUrl());
        if (request.getOpeningHours() != null) merchant.setOpeningHours(request.getOpeningHours());

        Merchant saved = merchantRepository.save(merchant);
        return toResponse(saved);
    }

    public MerchantPreferences getMyPreferences(UUID currentMerchantId) {
        Merchant merchant = merchantRepository.findById(currentMerchantId)
                .orElseThrow(() -> new MerchantNotFoundException(currentMerchantId));
        return merchant.getPreferences() != null
                ? merchant.getPreferences()
                : MerchantPreferences.builder().build();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MERCHANT_ID_BY_PROVIDER_USER, allEntries = true)
    public MerchantPreferences updateMyPreferences(UUID currentMerchantId, MerchantPreferences preferences) {
        Merchant merchant = merchantRepository.findById(currentMerchantId)
                .orElseThrow(() -> new MerchantNotFoundException(currentMerchantId));

        merchant.setPreferences(preferences);
        Merchant saved = merchantRepository.save(merchant);
        return saved.getPreferences();
    }

    @Transactional
    @CacheEvict(value = CacheConfig.MERCHANT_ID_BY_PROVIDER_USER, allEntries = true)
    public void delete(UUID currentMerchantId, UUID id) {
        ensureOwner(currentMerchantId, id);
        if (!merchantRepository.existsById(id)) {
            throw new MerchantNotFoundException(id);
        }
        merchantRepository.deleteById(id);
    }

    private void ensureOwner(UUID currentMerchantId, UUID id) {
        if (!currentMerchantId.equals(id)) {
            throw new ForbiddenException("Acesso negado");
        }
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
