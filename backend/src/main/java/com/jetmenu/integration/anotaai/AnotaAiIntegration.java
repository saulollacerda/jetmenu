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
}
