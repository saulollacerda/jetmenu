package com.jetmenu.integration.anotaai.services;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.anotaai.AnotaAIOrderDetailResponse;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.OrderItemExtraIngredient;
import com.jetmenu.order.OrderItemUnmatchedSubItem;
import com.jetmenu.order.ResolvedSubItems;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnotaAIExtraIngredientResolver — preço pago pelo cliente vs. custo de produção")
class AnotaAIExtraIngredientResolverTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private NotificationService notificationService;

    private static final UUID MERCHANT_ID = UUID.randomUUID();

    private AnotaAIExtraIngredientResolver resolver() {
        return new AnotaAIExtraIngredientResolver(ingredientRepository, notificationService);
    }

    private AnotaAIOrderDetailResponse.AnotaAISubItem subItem(String name, int quantity,
                                                              double price, double total) {
        AnotaAIOrderDetailResponse.AnotaAISubItem subItem = new AnotaAIOrderDetailResponse.AnotaAISubItem();
        subItem.setName(name);
        subItem.setQuantity(quantity);
        subItem.setPrice(price);
        subItem.setTotal(total);
        return subItem;
    }

    private Ingredient ingredient(String name, String canonical, BigDecimal costPerUnit,
                                  BigDecimal defaultQuantity) {
        return Ingredient.builder()
                .id(UUID.randomUUID())
                .name(name)
                .canonicalName(canonical)
                .unit("g")
                .costPerUnit(costPerUnit)
                .defaultQuantity(defaultQuantity)
                .build();
    }

    @Test
    @DisplayName("deve persistir o valor pago do payload para um subItem com preço (Açaí Premium 5,00)")
    void shouldPersistPaidPriceFromPayload() {
        // Ingrediente do catálogo local: 100g a R$ 0,05/g (custo de produção = R$ 5,00 por acaso não bate com o preço)
        Ingredient acaiPremium = ingredient("Açaí Premium", "acai premium",
                new BigDecimal("0.0500"), new BigDecimal("100"));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("acai premium"), any(UUID.class))).willReturn(Optional.of(acaiPremium));

        Set<String> missing = new HashSet<>();
        List<OrderItemExtraIngredient> extras = resolver().resolve(
                List.of(subItem("Açaí Premium", 1, 5.0, 5.0)),
                new ArrayList<>(), MERCHANT_ID, missing).extras();

        assertThat(extras).hasSize(1);
        OrderItemExtraIngredient extra = extras.get(0);

        // O valor pago vem literalmente do payload (subItem.total), NÃO de quantidade × preço.
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("5.00");
        assertThat(extra.getSalePricePerUnit()).isEqualByComparingTo("5.00");
    }

    @Test
    @DisplayName("valor pago NÃO pode ser derivado da gramatura do ingrediente (armadilha reais × gramas)")
    void shouldNotDeriveSalePriceFromIngredientQuantity() {
        // defaultQuantity = 200g. Se o preço fosse derivado de quantity × price
        // daria 200 × 1,5 = 300,00 — absurdo. O correto é o total do payload: R$ 1,50.
        Ingredient pistache = ingredient("Pistache", "pistache",
                new BigDecimal("0.0300"), new BigDecimal("200"));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("pistache"), any(UUID.class))).willReturn(Optional.of(pistache));

        List<OrderItemExtraIngredient> extras = resolver().resolve(
                List.of(subItem("Pistache", 1, 1.5, 1.5)),
                new ArrayList<>(), MERCHANT_ID, new HashSet<>()).extras();

        OrderItemExtraIngredient extra = extras.get(0);

        // A gramatura continua sendo gramatura...
        assertThat(extra.getQuantity()).isEqualByComparingTo("200");
        // ...e o valor pago continua sendo dinheiro do payload.
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("1.50");
        assertThat(extra.getSalePriceTotal()).isNotEqualByComparingTo("300.00");
    }

    @Test
    @DisplayName("subItem com preço 0,00 é complemento base e é distinguível de um adicional pago")
    void shouldHandleZeroPricedBaseComplement() {
        Ingredient leiteNinho = ingredient("Leite Ninho", "leite ninho",
                new BigDecimal("0.0200"), new BigDecimal("50"));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("leite ninho"), any(UUID.class))).willReturn(Optional.of(leiteNinho));

        List<OrderItemExtraIngredient> extras = resolver().resolve(
                List.of(subItem("Leite Ninho", 1, 0.0, 0.0)),
                new ArrayList<>(), MERCHANT_ID, new HashSet<>()).extras();

        OrderItemExtraIngredient extra = extras.get(0);

        // Complemento base: zero pago, mas ainda tem custo de produção.
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("0.00");
        assertThat(extra.getSalePricePerUnit()).isEqualByComparingTo("0.00");
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.0200");
    }

    @Test
    @DisplayName("deve copiar o total do payload sem recalcular quando subItem.quantity > 1")
    void shouldCopyPayloadTotalWithoutRecomputingForMultipleQuantity() {
        // Fixture real: leite ninho qty 2, price 0.5, total 1.0.
        Ingredient leiteNinho = ingredient("Leite Ninho", "leite ninho",
                new BigDecimal("0.0200"), new BigDecimal("50"));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("leite ninho"), any(UUID.class))).willReturn(Optional.of(leiteNinho));

        List<OrderItemExtraIngredient> extras = resolver().resolve(
                List.of(subItem("leite ninho", 2, 0.5, 1.0)),
                new ArrayList<>(), MERCHANT_ID, new HashSet<>()).extras();

        OrderItemExtraIngredient extra = extras.get(0);

        assertThat(extra.getSalePricePerUnit()).isEqualByComparingTo("0.50");
        assertThat(extra.getSalePriceTotal()).isEqualByComparingTo("1.00");
        // gramatura = defaultQuantity (50) × subItem.quantity (2)
        assertThat(extra.getQuantity()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("custo continua vindo do catálogo local e não é afetado pelo preço pago")
    void shouldKeepCostFromLocalCatalogUnaffectedByPrice() {
        Ingredient acaiPremium = ingredient("Açaí Premium", "acai premium",
                new BigDecimal("0.0500"), new BigDecimal("100"));
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("acai premium"), any(UUID.class))).willReturn(Optional.of(acaiPremium));

        List<OrderItemExtraIngredient> extras = resolver().resolve(
                List.of(subItem("Açaí Premium", 1, 5.0, 5.0)),
                new ArrayList<>(), MERCHANT_ID, new HashSet<>()).extras();

        OrderItemExtraIngredient extra = extras.get(0);

        // Custo = snapshot do catálogo local, independente do que o cliente pagou.
        assertThat(extra.getCostPerUnit()).isEqualByComparingTo("0.0500");
        assertThat(extra.getIngredientName()).isEqualTo("Açaí Premium");
        assertThat(extra.getIngredientUnit()).isEqualTo("g");
    }

    @Test
    @DisplayName("subItem sem ingrediente cadastrado vira registro não-casado (raw name, qtd e preço) e ainda notifica")
    void shouldRecordUnmatchedSubItemWhenNoIngredientMatches() {
        given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc(
                eq("nutella"), any(UUID.class))).willReturn(Optional.empty());

        Set<String> missing = new HashSet<>();
        ResolvedSubItems resolved = resolver().resolve(
                List.of(subItem("Nutella", 2, 3.0, 6.0)),
                new ArrayList<>(), MERCHANT_ID, missing);

        // Nenhum extra é criado (não há ingrediente), mas o subItem não some.
        assertThat(resolved.extras()).isEmpty();
        assertThat(resolved.unmatched()).hasSize(1);
        OrderItemUnmatchedSubItem unmatched = resolved.unmatched().get(0);
        assertThat(unmatched.getRawName()).isEqualTo("Nutella");
        assertThat(unmatched.getCanonicalName()).isEqualTo("nutella");
        assertThat(unmatched.getQuantity()).isEqualTo(2);
        assertThat(unmatched.getSalePricePerUnit()).isEqualByComparingTo("3.00");
        assertThat(unmatched.getSalePriceTotal()).isEqualByComparingTo("6.00");

        // A notificação MISSING_INGREDIENT continua sendo criada.
        then(notificationService).should().createMissingIngredient("Nutella", "nutella", MERCHANT_ID);
        assertThat(missing).contains("Nutella");
    }
}
