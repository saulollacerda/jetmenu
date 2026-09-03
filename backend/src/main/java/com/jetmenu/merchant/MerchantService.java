package com.jetmenu.merchant;

import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.common.ForbiddenException;
import com.jetmenu.config.CacheConfig;
import com.jetmenu.integration.anotaai.AnotaAIWebhookTokenService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class MerchantService {

    /** Prefixo da rota pública do webhook — ver {@code AnotaAIWebhookController}. */
    private static final String ANOTAAI_WEBHOOK_PATH_PREFIX = "/api/webhooks/anotaai/";

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionService subscriptionService;
    private final AnotaAIWebhookTokenService anotaAiWebhookTokenService;

    public MerchantService(MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder,
                           SubscriptionService subscriptionService,
                           AnotaAIWebhookTokenService anotaAiWebhookTokenService) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.subscriptionService = subscriptionService;
        this.anotaAiWebhookTokenService = anotaAiWebhookTokenService;
    }

    /**
     * Gera um segredo novo para o webhook da Anota.AI e descarta o anterior.
     * <p>
     * <b>Quem gera é o JetMenu</b>, com {@code SecureRandom}: lojista que inventa segredo
     * escreve {@code 123456}, e como a Anota.AI não assina as entregas esse valor é a única
     * coisa separando a contabilidade da loja de quem descobrir a URL.
     * <p>
     * Rotacionar <b>não muda a URL</b> — o lojista troca um campo no painel deles, não
     * recadastra o endpoint. Enquanto a rotação não é feita no painel, as entregas passam a
     * ser recusadas com 404; o sync de reconciliação diário cobre a janela.
     */
    @Transactional
    public AnotaAIWebhookConfigResponse rotateAnotaAiWebhookSecret(UUID currentMerchantId) {
        Merchant merchant = findWithAnotaAiIntegration(currentMerchantId);
        merchant.setAnotaAiWebhookSecret(anotaAiWebhookTokenService.generateSecret());
        merchantRepository.save(merchant);
        return toAnotaAiWebhookConfig(merchant);
    }

    public AnotaAIWebhookConfigResponse getAnotaAiWebhookConfig(UUID currentMerchantId) {
        return toAnotaAiWebhookConfig(findWithAnotaAiIntegration(currentMerchantId));
    }

    private Merchant findWithAnotaAiIntegration(UUID merchantId) {
        return merchantRepository.findByIdWithAnotaAiIntegration(merchantId)
                .orElseThrow(() -> new MerchantNotFoundException(merchantId));
    }

    private AnotaAIWebhookConfigResponse toAnotaAiWebhookConfig(Merchant merchant) {
        return AnotaAIWebhookConfigResponse.builder()
                .merchantId(merchant.getId())
                .webhookPath(ANOTAAI_WEBHOOK_PATH_PREFIX + merchant.getId())
                .webhookSecret(merchant.getAnotaAiWebhookSecret())
                .anotaAiMerchantId(merchant.getAnotaAiMerchantId())
                .build();
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
