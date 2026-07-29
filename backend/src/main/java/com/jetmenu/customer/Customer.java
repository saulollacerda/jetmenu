package com.jetmenu.customer;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "customers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Customer {

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

    private String phone;

    private String email;

    @Column(name = "external_id")
    private String externalId;

    @Column(length = 120)
    private String neighborhood;

    @Column(columnDefinition = "text")
    private String notes;
}

