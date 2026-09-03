package com.jetmenu.integration.anotaai;

import com.jetmenu.common.SharedSecrets;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Gera e confere o segredo do webhook da Anota.AI — o "Token Externo" que o lojista cola no
 * painel deles.
 * <p>
 * <b>Quem gera é o JetMenu.</b> Lojista que inventa segredo escreve {@code 123456}, e como a
 * Anota.AI não assina as entregas, esse valor é a única coisa separando a contabilidade da
 * loja de qualquer um que descubra a URL.
 */
@Service
public class AnotaAIWebhookTokenService {

    /** 256 bits — o mesmo peso do {@code whsec_} da Stripe. */
    private static final int SECRET_BYTES = 32;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final SecureRandom random = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    /** Comparação em tempo constante — ver {@link SharedSecrets#matches}. */
    public boolean matches(String expected, String provided) {
        return SharedSecrets.matches(expected, provided);
    }
}
