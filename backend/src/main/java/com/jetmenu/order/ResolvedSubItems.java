package com.jetmenu.order;

import java.util.List;

/**
 * Outcome of resolving the subItems/options of an imported order line: the ones that matched
 * a registered ingredient become {@link OrderItemExtraIngredient extras}; the ones that did
 * not become {@link OrderItemUnmatchedSubItem unmatched} entries so they can still be shown
 * (with a "create ingredient" action) instead of being silently dropped.
 */
public record ResolvedSubItems(
        List<OrderItemExtraIngredient> extras,
        List<OrderItemUnmatchedSubItem> unmatched) {
}
