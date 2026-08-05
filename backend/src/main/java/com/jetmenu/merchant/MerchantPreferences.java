package com.jetmenu.merchant;

import lombok.*;

import java.math.BigDecimal;

/**
 * Preferências por merchant (alertas / comportamento de cálculo).
 * Persistido como JSON em {@link Merchant#getPreferences()}.
 *
 * <p>Defaults conservadores: tudo desligado, exceto cálculo de margem em tempo real (true por
 * default).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPreferences {

    /** Recalcular margem ao editar produto/ficha técnica em tempo real. */
    @Builder.Default
    private boolean realtimeMarginCalc = true;

    /** Disparar notificação quando margem do produto cair abaixo de 50%. */
    @Builder.Default
    private boolean marginAlertBelow50Pct = false;

    /** Notificar quando um ingrediente referenciado em pedido não está no catálogo. */
    @Builder.Default
    private boolean warnUnregisteredIngredients = true;

    /** Incluir custo de itens de embalagem (Include.kind = PACKAGING) no totalCost. */
    @Builder.Default
    private boolean includePackagingCostInCost = true;

    /**
     * Margem ideal do pedido inteiro, em %, já descontadas as taxas (entrega, serviço e meio
     * de pagamento) — a mesma base do {@code marginPct} devolvido em cada pedido.
     *
     * <p>Diferente da margem ideal do produto, que é gravada como snapshot no item do pedido,
     * esta é uma configuração do lojista: cada pedido é comparado contra o valor vigente hoje,
     * não contra o que valia quando o pedido foi feito.</p>
     *
     * <p>{@code null} significa "não acompanhada" — nenhuma comparação é exibida.</p>
     */
    private BigDecimal targetOrderMarginPct;
}
