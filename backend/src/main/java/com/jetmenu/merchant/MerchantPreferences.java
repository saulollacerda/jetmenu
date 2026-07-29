package com.jetmenu.merchant;

import lombok.*;

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
}
