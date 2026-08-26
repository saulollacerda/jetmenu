package com.jetmenu.order;

import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderValueChangeService")
class OrderValueChangeServiceTest {

    @Mock
    private OrderValueChangeRepository orderValueChangeRepository;

    @InjectMocks
    private OrderValueChangeService orderValueChangeService;

    private Order order;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchant = Merchant.builder().id(UUID.randomUUID()).build();
        order = Order.builder().id(UUID.randomUUID()).merchant(merchant).build();
    }

    @Test
    @DisplayName("deve gravar uma linha quando totalValue muda")
    void shouldRecordWhenTotalValueChanges() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.ITEM_EDIT,
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"),
                new BigDecimal("60.00"), new BigDecimal("20.00"), new BigDecimal("40.00"));

        ArgumentCaptor<OrderValueChange> captor = ArgumentCaptor.forClass(OrderValueChange.class);
        then(orderValueChangeRepository).should().save(captor.capture());
        OrderValueChange saved = captor.getValue();

        assertThat(saved.getOrder()).isEqualTo(order);
        assertThat(saved.getMerchant()).isEqualTo(merchant);
        assertThat(saved.getSource()).isEqualTo(OrderValueChangeSource.ITEM_EDIT);
        assertThat(saved.getOldTotalValue()).isEqualByComparingTo("50.00");
        assertThat(saved.getNewTotalValue()).isEqualByComparingTo("60.00");
        assertThat(saved.getOldTotalCost()).isEqualByComparingTo("20.00");
        assertThat(saved.getNewTotalCost()).isEqualByComparingTo("20.00");
        assertThat(saved.getOldEstimatedProfit()).isEqualByComparingTo("30.00");
        assertThat(saved.getNewEstimatedProfit()).isEqualByComparingTo("40.00");
        assertThat(saved.getChangedAt()).isNotNull();
    }

    @Test
    @DisplayName("deve gravar uma linha quando só totalCost muda")
    void shouldRecordWhenOnlyTotalCostChanges() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.INGREDIENT_BACKFILL,
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"),
                new BigDecimal("50.00"), new BigDecimal("25.00"), new BigDecimal("25.00"));

        then(orderValueChangeRepository).should().save(any(OrderValueChange.class));
    }

    @Test
    @DisplayName("não deve gravar nada quando os três valores permanecem idênticos")
    void shouldNotRecordWhenNothingChanged() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.ITEM_EDIT,
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"),
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));

        then(orderValueChangeRepository).should(never()).save(any(OrderValueChange.class));
    }

    @Test
    @DisplayName("valores iguais com escalas diferentes (50.0 vs 50.00) não contam como mudança")
    void shouldNotRecordWhenValuesDifferOnlyInScale() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.ITEM_EDIT,
                new BigDecimal("50.0"), new BigDecimal("20.00"), new BigDecimal("30.00"),
                new BigDecimal("50.00"), new BigDecimal("20.0"), new BigDecimal("30.000"));

        then(orderValueChangeRepository).should(never()).save(any(OrderValueChange.class));
    }

    @Test
    @DisplayName("não deve gravar nada quando totalCost permanece nulo antes e depois")
    void shouldNotRecordWhenBothNull() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.ITEM_EDIT,
                new BigDecimal("50.00"), null, new BigDecimal("30.00"),
                new BigDecimal("50.00"), null, new BigDecimal("30.00"));

        then(orderValueChangeRepository).should(never()).save(any(OrderValueChange.class));
    }

    @Test
    @DisplayName("deve gravar quando totalCost sai de nulo (pedido legado) para um valor")
    void shouldRecordWhenTotalCostGoesFromNullToValue() {
        orderValueChangeService.recordIfChanged(order, OrderValueChangeSource.MANUAL_OVERRIDE,
                new BigDecimal("50.00"), null, new BigDecimal("50.00"),
                new BigDecimal("50.00"), new BigDecimal("20.00"), new BigDecimal("30.00"));

        then(orderValueChangeRepository).should().save(any(OrderValueChange.class));
    }
}
