package com.jetmenu.integration.ifood;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Estado da integração iFood de um merchant (1:1). Normaliza as colunas que antes
 * viviam achatadas em {@code merchants}: o merchant iFood vinculado, quando foi
 * autorizado, o opt-in da sincronia de pedidos e o último import de catálogo.
 * Os tokens OAuth continuam em {@code ifood_app_token} (app-level, não por merchant).
 */
@Entity
@Table(name = "ifood_integration")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IfoodIntegration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false, unique = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(name = "ifood_merchant_id")
    private String ifoodMerchantId;

    @Column(name = "ifood_authorized_at")
    private LocalDateTime ifoodAuthorizedAt;

    // columnDefinition default keeps dev (ddl-auto=update) happy on non-empty tables;
    // prod relies on the matching Flyway migration.
    @Builder.Default
    @Column(name = "ifood_order_sync_enabled", nullable = false,
            columnDefinition = "boolean not null default false")
    private boolean ifoodOrderSyncEnabled = false;

    @Column(name = "ifood_catalog_imported_at")
    private LocalDateTime ifoodCatalogImportedAt;
}
