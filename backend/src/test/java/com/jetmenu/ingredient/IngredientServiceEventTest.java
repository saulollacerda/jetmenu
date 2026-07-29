package com.jetmenu.ingredient;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.product.IncludeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngredientService — evento IngredientCreatedEvent")
class IngredientServiceEventTest {

    @Mock private IngredientRepository ingredientRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private NotificationService notificationService;
    @Mock private IncludeRepository includeRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngredientService ingredientService;

    private UUID merchantId;
    private IngredientRequest request;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(merchantId))
                .thenReturn(Merchant.builder().id(merchantId).build());

        request = IngredientRequest.builder()
                .name("Açaí Zero")
                .unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("100"))
                .build();
    }

    @Test
    @DisplayName("deve publicar IngredientCreatedEvent com merchantId, ingredientId e canonicalName corretos após criar ingrediente")
    void create_shouldPublishIngredientCreatedEvent() {
        UUID savedId = UUID.randomUUID();
        Ingredient saved = Ingredient.builder()
                .id(savedId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Açaí Zero")
                .canonicalName("acai zero")
                .unit("g")
                .costPerUnit(new BigDecimal("0.05"))
                .defaultQuantity(new BigDecimal("100"))
                .status(IngredientStatus.ACTIVE)
                .build();

        given(ingredientRepository.existsByNameAndMerchantId("Açaí Zero", merchantId)).willReturn(false);
        given(ingredientRepository.save(any())).willReturn(saved);

        ingredientService.create(merchantId, request);

        ArgumentCaptor<IngredientCreatedEvent> captor = ArgumentCaptor.forClass(IngredientCreatedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());

        IngredientCreatedEvent event = captor.getValue();
        assertThat(event.merchantId()).isEqualTo(merchantId);
        assertThat(event.ingredientId()).isEqualTo(savedId);
        assertThat(event.canonicalName()).isEqualTo("acai zero");
    }

    @Test
    @DisplayName("não deve publicar evento quando ingrediente já existe (DuplicateIngredientException)")
    void create_shouldNotPublishEventOnDuplicateName() {
        given(ingredientRepository.existsByNameAndMerchantId("Açaí Zero", merchantId)).willReturn(true);

        assertThatThrownBy(() -> ingredientService.create(merchantId, request))
                .isInstanceOf(DuplicateIngredientException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }
}
