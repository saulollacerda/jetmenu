package com.jetmenu.integration.anotaai;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Integração Anota.AI de um merchant (1:1). Normaliza a coluna {@code anota_ai_api_key}
 * que antes vivia achatada em {@code merchants}.
 */
@Entity
@Table(name = "anotaai_integration")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnotaAiIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(name = "anota_ai_api_key", columnDefinition = "TEXT")
    private String anotaAiApiKey;

    /**
     * O "Token Externo" que o lojista cadastra no painel da Anota.AI, gerado pelo JetMenu.
     * Chega em cada entrega no header {@code authorization}, com o valor cru — sem
     * {@code Bearer}, e sem nenhuma assinatura junto. É a única credencial do webhook.
     * <p>
     * Guardado em texto por escolha: o lojista precisa poder recopiar o valor na tela de
     * integração (o cadastro no painel da Anota.AI é manual, loja por loja).
     */
    @Column(name = "webhook_secret", columnDefinition = "TEXT")
    private String webhookSecret;

    /**
     * O id da loja no lado da Anota.AI ({@code merchant.id} do corpo da entrega) — um
     * ObjectId do Mongo, não um UUID. Não confundir com {@link #merchant}, que é o nosso id.
     * <p>
     * Existe para recusar cadastro cruzado: sem ele, colar a URL e o token de uma loja no
     * painel de outra passa em todas as validações e importa os pedidos na contabilidade
     * errada. Preenchido na primeira entrega e conferido em todas as seguintes — o mesmo
     * padrão de {@code ifood_integration.ifood_merchant_id}.
     */
    @Column(name = "anota_ai_merchant_id", columnDefinition = "TEXT")
    private String anotaAiMerchantId;
}
