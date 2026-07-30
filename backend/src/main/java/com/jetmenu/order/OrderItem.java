package com.jetmenu.order;

import com.jetmenu.product.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    /**
     * Custo estimado unitário do produto (snapshot no momento do pedido).
     * Calculado a partir da soma dos ingredientes da receita atual quando o pedido é criado.
     */
    @Column(name = "unit_cost", precision = 19, scale = 4)
    private BigDecimal unitCost;

    /**
     * Margem ideal (%) do produto no momento em que o item foi criado (snapshot).
     * Null em pedidos anteriores ao recurso e em produtos sem margem ideal — nunca é
     * reescrito quando o produto muda depois, para o histórico não mudar de alvo.
     */
    @Column(name = "target_margin_pct", precision = 5, scale = 2)
    private BigDecimal targetMarginPct;

    /**
     * Special instructions the customer wrote for this item ("sem cebola"), as imported from
     * the marketplace. Must be displayed on the order ticket (iFood Order homologation).
     * Null for manual orders and for imports without observations.
     */
    @Column(name = "observations", length = 1024)
    private String observations;

    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<OrderItemExtraIngredient> extraIngredients = new ArrayList<>();

    /**
     * SubItems/options of an imported order that matched no registered ingredient. Kept so
     * the order details can offer a "create ingredient" action instead of silently dropping
     * them. Empty for manual orders and for imports where every subItem matched.
     */
    @OneToMany(mappedBy = "orderItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @Builder.Default
    private List<OrderItemUnmatchedSubItem> unmatchedSubItems = new ArrayList<>();

    /**
     * Ids dos {@link com.jetmenu.product.Include}s da ficha técnica que o
     * operador desmarcou neste item (pedido manual). Vazio = ficha completa.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "order_item_excluded_includes",
            joinColumns = @JoinColumn(name = "order_item_id"))
    @Column(name = "include_id", nullable = false)
    @BatchSize(size = 30)
    @Builder.Default
    private java.util.Set<UUID> excludedIncludeIds = new java.util.HashSet<>();
}

