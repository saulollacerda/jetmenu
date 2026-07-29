package com.jetmenu.product;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Componente da composicao (ficha tecnica) de um {@link Product}.
 *
 * <p>Cada produto possui uma lista de includes — itens que sempre entram no custo do produto
 * (ex.: copo, colher, base, granola). Diferente do modelo antigo, includes nao referenciam
 * a tabela {@code ingredients}: o nome e o custo sao armazenados diretamente aqui, por produto.</p>
 *
 * <p>Custo total do produto = soma de ({@code cost} x {@code quantity}) de todos os includes.</p>
 */
@Entity
@Table(name = "includes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Include {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Product product;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal cost;

    @Column(nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind")
    private IncludeKind kind;

    @Column(name = "sort_order")
    private Integer sortOrder;
}
