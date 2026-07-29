package com.jetmenu.identity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Maps an external identity provider account (e.g. Supabase) to a JetMenu merchant.
 * <p>
 * The {@code providerUserId} is the stable user id issued by the provider (the JWT
 * {@code sub} claim). {@code merchantId} is stored as a plain column — resolution only
 * needs the id, and callers cache it.
 */
@Entity
@Table(
        name = "identities",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_user_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Identity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
