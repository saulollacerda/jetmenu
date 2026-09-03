package com.jetmenu.merchant;

import com.jetmenu.integration.anotaai.AnotaAiIntegration;
import com.jetmenu.integration.ifood.IfoodIntegration;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "merchants")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String merchantName;

    @Column(unique = true, nullable = false, length = 14)
    private String cnpj;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = true)
    private String password;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 500)
    private String address;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "opening_hours", columnDefinition = "jsonb")
    private List<OpeningHour> openingHours;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences", columnDefinition = "jsonb")
    private MerchantPreferences preferences;

    // Normalized 1:1 integrations. The FK lives on the child tables; Merchant owns the
    // lifecycle (cascade + orphanRemoval) so the convenience accessors below can create
    // or clear the row transparently. Excluded from equals/hashCode/toString to avoid
    // triggering lazy loads and bidirectional recursion.
    @OneToOne(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private IfoodIntegration ifoodIntegration;

    @OneToOne(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private AnotaAiIntegration anotaAiIntegration;

    // --- iFood integration convenience accessors (null-safe; create the 1:1 row on demand) ---

    public String getIfoodMerchantId() {
        return ifoodIntegration != null ? ifoodIntegration.getIfoodMerchantId() : null;
    }

    public void setIfoodMerchantId(String ifoodMerchantId) {
        if (ifoodIntegration == null && ifoodMerchantId == null) return;
        ensureIfoodIntegration().setIfoodMerchantId(ifoodMerchantId);
    }

    public LocalDateTime getIfoodAuthorizedAt() {
        return ifoodIntegration != null ? ifoodIntegration.getIfoodAuthorizedAt() : null;
    }

    public void setIfoodAuthorizedAt(LocalDateTime ifoodAuthorizedAt) {
        if (ifoodIntegration == null && ifoodAuthorizedAt == null) return;
        ensureIfoodIntegration().setIfoodAuthorizedAt(ifoodAuthorizedAt);
    }

    public boolean isIfoodOrderSyncEnabled() {
        return ifoodIntegration != null && ifoodIntegration.isIfoodOrderSyncEnabled();
    }

    public void setIfoodOrderSyncEnabled(boolean ifoodOrderSyncEnabled) {
        if (ifoodIntegration == null && !ifoodOrderSyncEnabled) return;
        ensureIfoodIntegration().setIfoodOrderSyncEnabled(ifoodOrderSyncEnabled);
    }

    public LocalDateTime getIfoodCatalogImportedAt() {
        return ifoodIntegration != null ? ifoodIntegration.getIfoodCatalogImportedAt() : null;
    }

    public void setIfoodCatalogImportedAt(LocalDateTime ifoodCatalogImportedAt) {
        if (ifoodIntegration == null && ifoodCatalogImportedAt == null) return;
        ensureIfoodIntegration().setIfoodCatalogImportedAt(ifoodCatalogImportedAt);
    }

    private IfoodIntegration ensureIfoodIntegration() {
        if (ifoodIntegration == null) {
            ifoodIntegration = IfoodIntegration.builder().merchant(this).build();
        }
        return ifoodIntegration;
    }

    // --- Anota.AI integration convenience accessors ---

    public String getAnotaAiApiKey() {
        return anotaAiIntegration != null ? anotaAiIntegration.getAnotaAiApiKey() : null;
    }

    public void setAnotaAiApiKey(String anotaAiApiKey) {
        if (anotaAiIntegration == null) {
            if (anotaAiApiKey == null) return;
            anotaAiIntegration = AnotaAiIntegration.builder().merchant(this).build();
        }
        anotaAiIntegration.setAnotaAiApiKey(anotaAiApiKey);
    }

    /** Segredo do webhook da Anota.AI — ver {@code AnotaAiIntegration.webhookSecret}. */
    public String getAnotaAiWebhookSecret() {
        return anotaAiIntegration != null ? anotaAiIntegration.getWebhookSecret() : null;
    }

    public void setAnotaAiWebhookSecret(String webhookSecret) {
        if (anotaAiIntegration == null && webhookSecret == null) return;
        ensureAnotaAiIntegration().setWebhookSecret(webhookSecret);
    }

    /** Id da loja do lado da Anota.AI — ver {@code AnotaAiIntegration.anotaAiMerchantId}. */
    public String getAnotaAiMerchantId() {
        return anotaAiIntegration != null ? anotaAiIntegration.getAnotaAiMerchantId() : null;
    }

    public void setAnotaAiMerchantId(String anotaAiMerchantId) {
        if (anotaAiIntegration == null && anotaAiMerchantId == null) return;
        ensureAnotaAiIntegration().setAnotaAiMerchantId(anotaAiMerchantId);
    }

    private AnotaAiIntegration ensureAnotaAiIntegration() {
        if (anotaAiIntegration == null) {
            anotaAiIntegration = AnotaAiIntegration.builder().merchant(this).build();
        }
        return anotaAiIntegration;
    }
}
