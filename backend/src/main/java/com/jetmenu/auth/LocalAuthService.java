package com.jetmenu.auth;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import com.jetmenu.merchant.DuplicateMerchantException;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.MerchantResponse;
import com.jetmenu.merchant.MerchantStatus;
import com.jetmenu.billing.SubscriptionService;
import com.jetmenu.security.LocalTokenIssuer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Dev-only local authentication: JetMenu owns the credentials (no Supabase). Registration
 * creates a {@link Merchant} (bcrypt password) + {@link Identity}; login verifies the password.
 * Both mint a JWT via {@link LocalTokenIssuer} that the app's local decoder accepts.
 * <p>
 * The identity {@code provider} comes from {@code app.auth.provider} (dev sets it to
 * {@code local}), so {@link AuthHelper} resolves these identities back to the merchant.
 */
@Service
@Profile({"dev", "test"})
public class LocalAuthService {

    @Value("${app.auth.provider:local}")
    private String provider = "local";

    private final MerchantRepository merchantRepository;
    private final IdentityRepository identityRepository;
    private final PasswordEncoder passwordEncoder;
    private final LocalTokenIssuer tokenIssuer;
    private final SubscriptionService subscriptionService;

    public LocalAuthService(MerchantRepository merchantRepository,
                            IdentityRepository identityRepository,
                            PasswordEncoder passwordEncoder,
                            LocalTokenIssuer tokenIssuer,
                            SubscriptionService subscriptionService) {
        this.merchantRepository = merchantRepository;
        this.identityRepository = identityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.subscriptionService = subscriptionService;
    }

    @Transactional
    public DevAuthResponse register(DevRegisterRequest request) {
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
                .password(passwordEncoder.encode(request.getPassword()))
                .status(MerchantStatus.ACTIVE)
                .createdAt(now)
                .build();
        Merchant saved = merchantRepository.save(merchant);

        String providerUserId = saved.getId().toString();
        identityRepository.save(Identity.builder()
                .merchantId(saved.getId())
                .provider(provider)
                .providerUserId(providerUserId)
                .createdAt(now)
                .build());

        // Mirrors MerchantService.create: without a subscription row the merchant can never be
        // activated, because SubscriptionActivationService updates an existing subscription and
        // throws SubscriptionNotFoundException otherwise. A merchant registered here would then
        // pay in Stripe and have the webhook answered 404 — money taken, nothing unblocked.
        subscriptionService.createPendingSubscription(saved.getId());

        String token = tokenIssuer.issue(providerUserId, saved.getEmail());
        return DevAuthResponse.builder().accessToken(token).merchant(toResponse(saved)).build();
    }

    @Transactional
    public DevAuthResponse login(DevLoginRequest request) {
        Merchant merchant = merchantRepository.findByEmail(request.getEmail())
                .orElseThrow(InvalidCredentialsException::new);
        if (merchant.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            throw new InvalidCredentialsException();
        }

        String providerUserId = merchant.getId().toString();
        identityRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .orElseGet(() -> identityRepository.save(Identity.builder()
                        .merchantId(merchant.getId())
                        .provider(provider)
                        .providerUserId(providerUserId)
                        .createdAt(LocalDateTime.now())
                        .build()));

        String token = tokenIssuer.issue(providerUserId, merchant.getEmail());
        return DevAuthResponse.builder().accessToken(token).merchant(toResponse(merchant)).build();
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
