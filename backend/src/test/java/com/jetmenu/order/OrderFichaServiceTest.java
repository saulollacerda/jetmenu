package com.jetmenu.order;

import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientNotFoundException;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderFichaService — ficha do pedido (insumos por pedido)")
class OrderFichaServiceTest {

    @Mock
    private OrderFichaLineRepository orderFichaLineRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @InjectMocks
    private OrderFichaService orderFichaService;

    private UUID merchantId;
    private UUID ingredientId;
    private Ingredient sacola;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        ingredientId = UUID.randomUUID();
        sacola = Ingredient.builder()
                .id(ingredientId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Sacola de entrega")
                .unit("un")
                .costPerUnit(new BigDecimal("0.50"))
                .build();
    }

    private OrderFichaLine line(Ingredient ingredient, BigDecimal quantity, Integer sortOrder) {
        return OrderFichaLine.builder()
                .id(UUID.randomUUID())
                .merchant(Merchant.builder().id(merchantId).build())
                .ingredient(ingredient)
                .quantity(quantity)
                .sortOrder(sortOrder)
                .build();
    }

    // ---------------------------------------------------------------- findByMerchant

    @Test
    @DisplayName("findByMerchant devolve as linhas com o custo por pedido")
    void findByMerchantReturnsLinesWithCost() {
        given(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId))
                .willReturn(List.of(line(sacola, new BigDecimal("2"), 0)));

        OrderFichaResponse response = orderFichaService.findByMerchant(merchantId);

        assertThat(response.getLines()).hasSize(1);
        OrderFichaLineResponse first = response.getLines().get(0);
        assertThat(first.getIngredientId()).isEqualTo(ingredientId);
        assertThat(first.getIngredientName()).isEqualTo("Sacola de entrega");
        assertThat(first.getIngredientUnit()).isEqualTo("un");
        assertThat(first.getQuantity()).isEqualByComparingTo("2");
        assertThat(first.getCostPerUnit()).isEqualByComparingTo("0.50");
        assertThat(first.getTotalCost()).isEqualByComparingTo("1.00");
        assertThat(response.getTotalCost()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("findByMerchant sem ficha configurada devolve lista vazia e custo zero")
    void findByMerchantWithoutConfigReturnsEmpty() {
        given(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId))
                .willReturn(List.of());

        OrderFichaResponse response = orderFichaService.findByMerchant(merchantId);

        assertThat(response.getLines()).isEmpty();
        assertThat(response.getTotalCost()).isEqualByComparingTo("0");
    }

    // ---------------------------------------------------------------- replace

    @Test
    @DisplayName("replace troca a ficha inteira e grava a ordem informada")
    void replaceStoresLinesInOrder() {
        UUID otherId = UUID.randomUUID();
        Ingredient guardanapo = Ingredient.builder()
                .id(otherId).name("Guardanapo").unit("un")
                .costPerUnit(new BigDecimal("0.03")).build();
        given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(sacola));
        given(ingredientRepository.findByIdAndMerchantId(otherId, merchantId)).willReturn(Optional.of(guardanapo));
        given(merchantRepository.getReferenceById(merchantId)).willReturn(Merchant.builder().id(merchantId).build());
        given(orderFichaLineRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));

        OrderFichaResponse response = orderFichaService.replace(merchantId, OrderFichaRequest.builder()
                .lines(List.of(
                        OrderFichaLineRequest.builder().ingredientId(ingredientId).quantity(BigDecimal.ONE).build(),
                        OrderFichaLineRequest.builder().ingredientId(otherId).quantity(new BigDecimal("2")).build()))
                .build());

        verify(orderFichaLineRepository).deleteAllByMerchantId(merchantId);
        assertThat(response.getLines()).extracting(OrderFichaLineResponse::getIngredientName)
                .containsExactly("Sacola de entrega", "Guardanapo");
        // (1 × 0.50) + (2 × 0.03) = 0.56 por pedido
        assertThat(response.getTotalCost()).isEqualByComparingTo("0.56");
    }

    @Test
    @DisplayName("replace com lista vazia limpa a ficha — volta ao custo zero")
    void replaceWithEmptyListClearsFicha() {
        OrderFichaResponse response = orderFichaService.replace(merchantId,
                OrderFichaRequest.builder().lines(List.of()).build());

        verify(orderFichaLineRepository).deleteAllByMerchantId(merchantId);
        assertThat(response.getLines()).isEmpty();
        assertThat(response.getTotalCost()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("replace rejeita ingrediente de outro lojista / inexistente")
    void replaceRejectsUnknownIngredient() {
        given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderFichaService.replace(merchantId, OrderFichaRequest.builder()
                .lines(List.of(OrderFichaLineRequest.builder()
                        .ingredientId(ingredientId).quantity(BigDecimal.ONE).build()))
                .build()))
                .isInstanceOf(IngredientNotFoundException.class);

        verify(orderFichaLineRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("replace rejeita o mesmo ingrediente duas vezes (unique merchant+ingredient)")
    void replaceRejectsDuplicateIngredient() {
        given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(sacola));

        assertThatThrownBy(() -> orderFichaService.replace(merchantId, OrderFichaRequest.builder()
                .lines(List.of(
                        OrderFichaLineRequest.builder().ingredientId(ingredientId).quantity(BigDecimal.ONE).build(),
                        OrderFichaLineRequest.builder().ingredientId(ingredientId).quantity(BigDecimal.ONE).build()))
                .build()))
                .isInstanceOf(DuplicateOrderFichaIngredientException.class);

        verify(orderFichaLineRepository, never()).saveAll(any());
    }

    // ---------------------------------------------------------------- buildSnapshot

    @Test
    @DisplayName("buildSnapshot copia nome, unidade, quantidade e custo do ingrediente")
    void buildSnapshotCopiesIngredientData() {
        given(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId))
                .willReturn(List.of(line(sacola, new BigDecimal("2"), 0)));

        List<OrderFichaIngredient> snapshot = orderFichaService.buildSnapshot(merchantId);

        assertThat(snapshot).hasSize(1);
        OrderFichaIngredient first = snapshot.get(0);
        assertThat(first.getIngredient()).isEqualTo(sacola);
        assertThat(first.getIngredientName()).isEqualTo("Sacola de entrega");
        assertThat(first.getIngredientUnit()).isEqualTo("un");
        assertThat(first.getQuantity()).isEqualByComparingTo("2");
        assertThat(first.getCostPerUnit()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("buildSnapshot sem ficha configurada devolve lista vazia — no-op")
    void buildSnapshotWithoutConfigReturnsEmpty() {
        given(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId))
                .willReturn(List.of());

        assertThat(orderFichaService.buildSnapshot(merchantId)).isEmpty();
    }

    @Test
    @DisplayName("buildSnapshot congela o custo: alterar o ingrediente depois não muda o snapshot")
    void buildSnapshotFreezesCost() {
        given(orderFichaLineRepository.findAllByMerchantIdOrderBySortOrderAsc(merchantId))
                .willReturn(List.of(line(sacola, BigDecimal.ONE, 0)));

        List<OrderFichaIngredient> snapshot = orderFichaService.buildSnapshot(merchantId);

        // o lojista reajusta o custo da sacola depois do pedido
        sacola.setCostPerUnit(new BigDecimal("9.99"));
        sacola.setName("Sacola nova");

        assertThat(snapshot.get(0).getCostPerUnit()).isEqualByComparingTo("0.50");
        assertThat(snapshot.get(0).getIngredientName()).isEqualTo("Sacola de entrega");
    }
}
