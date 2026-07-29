package com.jetmenu.ingredient;

import java.text.Normalizer;

/**
 * Converte o nome de um ingrediente em uma forma canônica usada como chave de dedupe.
 * Aplica: trim, lowercase, remoção de diacríticos (acentos) e colapso de whitespace
 * interno para um único espaço.
 *
 * <p>Ex.: {@code "Creme  de Chocolate"} e {@code "CRÈME DE CHOCOLATE"} colapsam ambos
 * para {@code "creme de chocolate"}.
 */
public final class IngredientNameNormalizer {

    private IngredientNameNormalizer() {}

    public static String normalize(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "";
        String decomposed = Normalizer.normalize(trimmed, Normalizer.Form.NFD);
        String stripped = decomposed.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.toLowerCase().replaceAll("\\s+", " ");
    }
}
