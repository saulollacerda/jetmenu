package com.jetmenu.notification;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    /**
     * Canonical reference key used for deduplication and auto-resolution.
     * For {@link NotificationType#MISSING_INGREDIENT}: the canonical (normalized) ingredient name.
     */
    @Column(name = "reference_data")
    private String referenceData;

    /**
     * Original display value (preserves case/accents) used to pre-fill UI forms.
     * For {@link NotificationType#MISSING_INGREDIENT}: the raw ingredient name from the order.
     */
    @Column(name = "reference_display")
    private String referenceDisplay;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
