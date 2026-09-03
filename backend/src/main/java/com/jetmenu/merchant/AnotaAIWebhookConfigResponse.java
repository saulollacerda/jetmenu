package com.jetmenu.merchant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * O que a tela de integração precisa mostrar para o lojista cadastrar o webhook no painel da
 * Anota.AI — cadastro manual, loja por loja, então os dois valores vão prontos para copiar.
 * <p>
 * O segredo volta em texto de propósito: o lojista precisa poder recopiá-lo. É a mesma
 * escolha da Stripe com o {@code whsec_} e da {@code anota_ai_api_key} que já vive nessa
 * tabela. O endpoint que devolve isso é autenticado como qualquer outro {@code /me}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnotaAIWebhookConfigResponse {

    private UUID merchantId;

    /**
     * O caminho, não a URL completa: quem sabe o host público do backend é o frontend, que
     * já fala com ele. Evita mais uma variável de ambiente só para montar uma string.
     */
    private String webhookPath;

    /** O "Token Externo" a colar no painel da Anota.AI. {@code null} até a primeira geração. */
    private String webhookSecret;

    /**
     * A loja vinculada do lado da Anota.AI, preenchida na primeira entrega recebida.
     * {@code null} enquanto nenhuma chegou — serve à tela para dizer se o webhook já rodou.
     */
    private String anotaAiMerchantId;
}
