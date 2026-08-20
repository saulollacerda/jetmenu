package com.jetmenu.order;

import com.jetmenu.customer.Customer;
import com.jetmenu.fee.Fee;
import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(nullable = false)
    private LocalDateTime dateTime;

    /**
     * Última gravação do pedido, escrita pelo Hibernate na criação e em toda edição.
     * Serve para distinguir um pedido importado intacto de um que passou pela tela de
     * edição — informação que faltou ao diagnosticar o total errado em pedidos com frete.
     */
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fee_id", nullable = true)
    private Fee fee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private BigDecimal totalValue;

    @Column(nullable = false)
    private BigDecimal estimatedProfit;

    @Column(name = "delivery_fee", precision = 19, scale = 4)
    private BigDecimal deliveryFee;

    /**
     * Taxa de serviço repassada ao iFood (pedidos do canal iFood importados via Anota.AI,
     * campo {@code additionalFees} do payload). Está inclusa no {@code totalValue}, mas não
     * é receita do restaurante — é deduzida do lucro e excluída da base da margem, como a
     * {@link #deliveryFee}. Nula em pedidos manuais e em pedidos sem taxa de serviço.
     */
    @Column(name = "service_fee", precision = 19, scale = 4)
    private BigDecimal serviceFee;

    @Column(name = "total_cost", precision = 19, scale = 4)
    private BigDecimal totalCost;

    @Column(name = "external_order_id")
    private String externalOrderId;

    @Column(name = "extra_info", length = 1024)
    private String extraInfo;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin")
    private OrderOrigin origin;

    // ------------------------------------------------------------------------------------
    // Descriptive data imported from the marketplace (iFood Order module homologation).
    // Every field below is nullable and display-only: none of them takes part in the
    // financial math (totalValue, deliveryFee, serviceFee, totalCost, estimatedProfit).
    // Orders imported before this feature — and every manual order — simply carry nulls.
    // ------------------------------------------------------------------------------------

    /** Friendly order id shown to the merchant (iFood {@code displayId}), e.g. "3421". */
    @Column(name = "display_id", length = 60)
    private String displayId;

    /** DELIVERY / TAKEOUT / DINE_IN — decides which marketplace action applies to the order. */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", length = 20)
    private OrderType orderType;

    /** IMMEDIATE / SCHEDULED. */
    @Enumerated(EnumType.STRING)
    @Column(name = "order_timing", length = 20)
    private OrderTiming orderTiming;

    /**
     * CPF/CNPJ the customer asked to put on the invoice ({@code customer.documentNumber}).
     * Kept on the order, not on {@link Customer}: it is informed per order and may be a
     * third party's document.
     */
    @Column(name = "customer_document", length = 32)
    private String customerDocument;

    /** Amount already paid online through the marketplace. */
    @Column(name = "payment_prepaid_amount", precision = 19, scale = 4)
    private BigDecimal paymentPrepaidAmount;

    /** Amount still to be collected by the merchant on delivery. */
    @Column(name = "payment_pending_amount", precision = 19, scale = 4)
    private BigDecimal paymentPendingAmount;

    /** Sum of every coupon/discount applied to the order. Recorded for display only. */
    @Column(name = "discount_total", precision = 19, scale = 4)
    private BigDecimal discountTotal;

    /** Share of {@link #discountTotal} sponsored by iFood. */
    @Column(name = "discount_ifood_value", precision = 19, scale = 4)
    private BigDecimal discountIfoodValue;

    /** Share of {@link #discountTotal} paid by the merchant (Loja). */
    @Column(name = "discount_merchant_value", precision = 19, scale = 4)
    private BigDecimal discountMerchantValue;

    @Column(name = "delivery_mode", length = 40)
    private String deliveryMode;

    /** IFOOD or MERCHANT — who performs the delivery. */
    @Column(name = "delivered_by", length = 40)
    private String deliveredBy;

    @Column(name = "delivery_date_time")
    private LocalDateTime deliveryDateTime;

    /** Free-text delivery instructions that must be shown on the order ticket. */
    @Column(name = "delivery_observations", length = 1024)
    private String deliveryObservations;

    /** Collection code presented by the courier / used for takeout pickup. */
    @Column(name = "pickup_code", length = 40)
    private String pickupCode;

    @Column(name = "takeout_mode", length = 40)
    private String takeoutMode;

    @Column(name = "takeout_date_time")
    private LocalDateTime takeoutDateTime;

    /**
     * Payment breakdown of an imported order (card brand, cash change). Empty for manual
     * orders and for orders imported before this feature.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OrderPaymentMethod> paymentMethods;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OrderItem> items;

    /**
     * Snapshot da ficha do pedido: insumos cobrados UMA VEZ neste pedido, independentemente
     * da quantidade de itens (sacola, guardanapo). Copiado de {@link OrderFichaLine} na
     * criação/importação. Vazio/nulo em pedidos anteriores à V17 e em lojistas sem ficha
     * do pedido configurada — nesse caso o custo do pedido não muda.
     */
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 30)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<OrderFichaIngredient> orderFicha;

    // ------------------------------------------------------------------------------------
    // Correção manual do valor final/custo do pedido. O lojista pode sobrescrever
    // totalValue/totalCost para registrar a realidade (desconto dado por telefone, insumo
    // perdido, reembolso ao entregador) que o cálculo automático a partir dos itens não
    // tem como prever. Antes da primeira correção, os valores calculados pelo sistema são
    // preservados aqui — a correção manual nunca é destrutiva.
    // ------------------------------------------------------------------------------------

    /**
     * Snapshot de {@code totalValue}, {@code totalCost} e {@code estimatedProfit} calculados
     * pelo sistema, tirado uma única vez na primeira correção manual. Correções manuais
     * seguintes não sobrescrevem o snapshot — ele guarda sempre o valor original de verdade.
     * Nulos em todo pedido nunca corrigido manualmente.
     */
    @Column(name = "original_total_value", precision = 19, scale = 4)
    private BigDecimal originalTotalValue;

    @Column(name = "original_total_cost", precision = 19, scale = 4)
    private BigDecimal originalTotalCost;

    @Column(name = "original_estimated_profit", precision = 19, scale = 4)
    private BigDecimal originalEstimatedProfit;

    /** Momento da primeira correção manual do valor final/custo. Nulo enquanto não houver override. */
    @Column(name = "values_overridden_at")
    private LocalDateTime valuesOverriddenAt;
}

