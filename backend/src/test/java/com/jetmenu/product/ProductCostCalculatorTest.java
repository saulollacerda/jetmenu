package com.jetmenu.product;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ProductCostCalculator")
class ProductCostCalculatorTest {

    private Include include(String name, String cost, String quantity, IncludeKind kind) {
        return Include.builder()
                .name(name)
                .cost(new BigDecimal(cost))
                .quantity(new BigDecimal(quantity))
                .kind(kind)
                .build();
    }

    @Nested
    @DisplayName("computeUnitCost — receita completa (catálogo do produto)")
    class ComputeUnitCost {

        @Test
        @DisplayName("soma todos os includes (cost × quantity), independente do kind")
        void sumsAllIncludes() {
            List<Include> includes = List.of(
                    include("Copo", "0.30", "1", IncludeKind.PACKAGING),
                    include("Granola", "0.05", "40", IncludeKind.INGREDIENT),
                    include("Açaí base", "0.10", "150", null)
            );
            // 0.30 + (0.05×40=2.00) + (0.10×150=15.00) = 17.30
            assertThat(ProductCostCalculator.computeUnitCost(includes)).isEqualByComparingTo("17.30");
        }

        @Test
        @DisplayName("retorna zero para lista vazia ou nula")
        void zeroForEmptyOrNull() {
            assertThat(ProductCostCalculator.computeUnitCost(null)).isEqualByComparingTo("0");
            assertThat(ProductCostCalculator.computeUnitCost(List.of())).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("computeOrderBaseCost — base do pedido conta apenas PACKAGING")
    class ComputeOrderBaseCost {

        @Test
        @DisplayName("soma apenas includes do tipo PACKAGING (cost × quantity)")
        void sumsOnlyPackaging() {
            List<Include> includes = List.of(
                    include("Copo", "0.30", "1", IncludeKind.PACKAGING),
                    include("Embalagem", "0.20", "1", IncludeKind.PACKAGING),
                    include("Granola", "0.05", "40", IncludeKind.INGREDIENT)
            );
            // só PACKAGING: 0.30 + 0.20 = 0.50 (granola NÃO entra na base)
            assertThat(ProductCostCalculator.computeOrderBaseCost(includes)).isEqualByComparingTo("0.50");
        }

        @Test
        @DisplayName("ignora includes do tipo INGREDIENT")
        void ignoresIngredientKind() {
            List<Include> includes = List.of(
                    include("Leite ninho", "0.05", "40", IncludeKind.INGREDIENT)
            );
            assertThat(ProductCostCalculator.computeOrderBaseCost(includes)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("ignora includes legados sem kind (null)")
        void ignoresLegacyNullKind() {
            List<Include> includes = List.of(
                    include("Açaí base", "0.10", "150", null)
            );
            assertThat(ProductCostCalculator.computeOrderBaseCost(includes)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("retorna zero para lista vazia ou nula")
        void zeroForEmptyOrNull() {
            assertThat(ProductCostCalculator.computeOrderBaseCost(null)).isEqualByComparingTo("0");
            assertThat(ProductCostCalculator.computeOrderBaseCost(List.of())).isEqualByComparingTo("0");
        }
    }

    @Nested
    @DisplayName("computeSelectedCost — insumos do pedido manual (PACKAGING e legados) menos exclusões")
    class ComputeSelectedCost {

        private Include includeWithId(java.util.UUID id, String name, String cost, String quantity, IncludeKind kind) {
            Include inc = include(name, cost, quantity, kind);
            inc.setId(id);
            return inc;
        }

        @Test
        @DisplayName("soma PACKAGING e legados sem kind; INGREDIENT fica de fora mesmo sem exclusão")
        void sumsInsumosSkippingIngredientKind() {
            List<Include> includes = List.of(
                    include("Copo", "0.30", "1", IncludeKind.PACKAGING),
                    include("Granola", "0.05", "40", IncludeKind.INGREDIENT),
                    include("Açaí base", "0.10", "150", null)
            );
            // 0.30 + 15.00 = 15.30 (Granola INGREDIENT não entra: só conta como extra)
            assertThat(ProductCostCalculator.computeSelectedCost(includes, null))
                    .isEqualByComparingTo("15.30");
        }

        @Test
        @DisplayName("exclui insumos cujo id está na lista de exclusões")
        void skipsExcludedIds() {
            java.util.UUID copoId = java.util.UUID.randomUUID();
            List<Include> includes = List.of(
                    includeWithId(copoId, "Copo", "0.30", "1", IncludeKind.PACKAGING),
                    includeWithId(java.util.UUID.randomUUID(), "Embalagem", "0.20", "1", IncludeKind.PACKAGING)
            );
            assertThat(ProductCostCalculator.computeSelectedCost(includes, java.util.Set.of(copoId)))
                    .isEqualByComparingTo("0.20");
        }

        @Test
        @DisplayName("conjunto de exclusões vazio equivale a todos os insumos")
        void emptyExclusionsMeansFullRecipe() {
            List<Include> includes = List.of(
                    include("Copo", "0.10", "1", null)
            );
            assertThat(ProductCostCalculator.computeSelectedCost(includes, java.util.Set.of()))
                    .isEqualByComparingTo("0.10");
        }

        @Test
        @DisplayName("retorna zero para lista vazia ou nula")
        void zeroForEmptyOrNull() {
            assertThat(ProductCostCalculator.computeSelectedCost(null, null)).isEqualByComparingTo("0");
            assertThat(ProductCostCalculator.computeSelectedCost(List.of(), null)).isEqualByComparingTo("0");
        }
    }
}
