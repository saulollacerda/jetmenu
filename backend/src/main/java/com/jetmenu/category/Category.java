package com.jetmenu.category;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(nullable = false)
    private String name;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "color_hex", length = 7)
    private String colorHex;

    // columnDefinition default is required for dev (ddl-auto=update on non-empty tables);
    // prod relies on the matching Flyway migration.
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 20,
            columnDefinition = "varchar(20) not null default 'JETMENU'")
    private CatalogOrigin origin = CatalogOrigin.JETMENU;
}

