package com.jetmenu.order;

import com.jetmenu.merchant.Merchant;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trilha de auditoria de uma mudança em {@code totalValue}/{@code totalCost}/
 * {@code estimatedProfit} de um pedido já existente. Não serve para o lojista — é para o
 * desenvolvedor conseguir olhar "tudo que aconteceu com este pedido, em ordem" quando um
 * merchant reporta que o valor ou o lucro de um pedido está errado.
 *
 * <p>Uma linha é gravada só quando a operação efetivamente muda algum dos três valores —
 * ver {@link OrderValueChangeSource} para as origens possíveis. A CRIAÇÃO de um pedido
 * nunca gera linha aqui: só mudanças em um pedido que já existia.
 */
@Entity
@Table(name = "order_value_changes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValueChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Order order;

    // Pedido já é escopado por merchant, mas toda tabela deste app carrega merchant_id
    // direto — evita um JOIN em orders só para filtrar/isolar por lojista ao debugar.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Merchant merchant;

    @Column(name = "old_total_value", precision = 19, scale = 4)
    private BigDecimal oldTotalValue;

    @Column(name = "new_total_value", precision = 19, scale = 4)
    private BigDecimal newTotalValue;

    @Column(name = "old_total_cost", precision = 19, scale = 4)
    private BigDecimal oldTotalCost;

    @Column(name = "new_total_cost", precision = 19, scale = 4)
    private BigDecimal newTotalCost;

    @Column(name = "old_estimated_profit", precision = 19, scale = 4)
    private BigDecimal oldEstimatedProfit;

    @Column(name = "new_estimated_profit", precision = 19, scale = 4)
    private BigDecimal newEstimatedProfit;

    /** Hora de Brasília (mesma zona usada pelo resto do fluxo de pedidos), não a do servidor. */
    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    private OrderValueChangeSource source;
}
