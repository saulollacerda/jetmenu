package com.jetmenu.integration;

import com.jetmenu.identity.Identity;
import com.jetmenu.identity.IdentityRepository;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.merchant.MerchantStatus;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Superclasse para testes de integração: sobe o contexto Spring inteiro (incluindo
 * Postgres real configurado em {@code application.properties} de teste), aplica
 * {@link Transactional} para rollback após cada método (isolamento entre testes
 * sem precisar limpar tabelas manualmente).
 *
 * <p>Disponibiliza um {@link Merchant} persistido por padrão via {@link #createMerchant()} —
 * a maioria das entidades exige FK para merchant, então este é o helper mais comum.</p>
 */
@SpringBootTest
@Transactional
public abstract class IntegrationTestBase {

    @Autowired
    protected MerchantRepository merchantRepository;

    @Autowired
    protected IdentityRepository identityRepository;

    /** Gera CNPJ único (14 dígitos) sequencial para evitar colisão entre testes. */
    private static final AtomicLong CNPJ_SEQ = new AtomicLong(10_000_000_000_000L);

    /**
     * Cria e persiste um Merchant novo com dados únicos. Use quando o teste precisa
     * de um merchant pronto para usar como FK.
     */
    protected Merchant createMerchant() {
        return createMerchant("Merchant-" + UUID.randomUUID());
    }

    protected Merchant createMerchant(String merchantName) {
        Merchant merchant = Merchant.builder()
                .merchantName(merchantName)
                .cnpj(String.valueOf(CNPJ_SEQ.incrementAndGet()))
                .email(UUID.randomUUID() + "@example.com")
                .password("encoded-password")
                .status(MerchantStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();
        return merchantRepository.save(merchant);
    }

    /**
     * Cria um merchant, registra uma Identity (provider=supabase) e coloca o
     * provider_user_id no SecurityContext como principal. Use quando o teste exercita
     * o fluxo de autenticação resolvido por {@code AuthHelper}.
     */
    protected Merchant createMerchantAndAuthenticate() {
        Merchant merchant = createMerchant();
        authenticateAs(merchant);
        return merchant;
    }

    /**
     * Registra uma Identity (provider=supabase) para o merchant e autentica o contexto
     * com o provider_user_id correspondente (o que o {@code JwtAuthFilter} colocaria).
     */
    protected void authenticateAs(Merchant merchant) {
        String providerUserId = UUID.randomUUID().toString();
        identityRepository.save(Identity.builder()
                .merchantId(merchant.getId())
                .provider("supabase")
                .providerUserId(providerUserId)
                .createdAt(LocalDateTime.now())
                .build());
        var auth = new TestingAuthenticationToken(providerUserId, null);
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
