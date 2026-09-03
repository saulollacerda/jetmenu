package com.jetmenu.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Comparação de segredos compartilhados que chegam por header.
 *
 * <p>Existe para que a regra seja escrita uma vez só: cada lugar que compara credencial à mão
 * é um lugar onde alguém pode usar {@code equals} sem perceber.
 */
public final class SharedSecrets {

    private SharedSecrets() {}

    /**
     * Compara em <b>tempo constante</b>. {@code String.equals} retorna no primeiro byte
     * diferente, e essa diferença de tempo é medível pela rede: dá para descobrir o segredo um
     * caractere por vez.
     *
     * <p>Valor nulo ou em branco nunca casa — nem quando os dois lados estão em branco. Isso
     * mantém "não configurado" como sinônimo de "recusa tudo", em vez de "aceita quem também
     * não mandou nada".
     */
    public static boolean matches(String expected, String provided) {
        if (expected == null || expected.isBlank() || provided == null || provided.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8));
    }
}
