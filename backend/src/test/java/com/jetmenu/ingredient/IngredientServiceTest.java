package com.jetmenu.ingredient;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.notification.NotificationService;
import com.jetmenu.product.IncludeRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("IngredientService")
class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private IncludeRepository includeRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private IngredientService ingredientService;

    private UUID merchantId;
    private UUID ingredientId;
    private Ingredient ingredient;
    private IngredientRequest ingredientRequest;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        ingredientId = UUID.randomUUID();

        ingredientRequest = IngredientRequest.builder()
                .name("Farinha de Trigo")
                .unit("kg")
                .costPerUnit(new BigDecimal("4.50"))
                .defaultQuantity(new BigDecimal("0.20"))
                .build();

        ingredient = Ingredient.builder()
                .id(ingredientId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Farinha de Trigo")
                .canonicalName("farinha de trigo")
                .unit("kg")
                .costPerUnit(new BigDecimal("4.50"))
                .defaultQuantity(new BigDecimal("0.20"))
                .status(IngredientStatus.ACTIVE)
                .build();

        lenient().when(includeRepository.countByLowercaseNameInForMerchant(any(), any()))
                .thenReturn(java.util.List.of());
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar ingrediente com dados válidos e retornar IngredientResponse")
        void shouldCreateIngredientAndReturnResponse() {
            given(ingredientRepository.existsByNameAndMerchantId(ingredientRequest.getName(), merchantId)).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willReturn(ingredient);

            IngredientResponse result = ingredientService.create(merchantId, ingredientRequest);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo(ingredientRequest.getName());
            assertThat(result.getUnit()).isEqualTo(ingredientRequest.getUnit());
            assertThat(result.getCostPerUnit()).isEqualByComparingTo(ingredientRequest.getCostPerUnit());
            assertThat(result.getDefaultQuantity()).isEqualByComparingTo(ingredientRequest.getDefaultQuantity());
            then(ingredientRepository).should().save(argThat(i -> merchantId.equals(i.getMerchant().getId())));
        }

        @Test
        @DisplayName("deve criar ingrediente com status ACTIVE por padrão")
        void shouldCreateIngredientWithActiveStatusByDefault() {
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willReturn(ingredient);

            IngredientResponse result = ingredientService.create(merchantId, ingredientRequest);

            assertThat(result.getStatus()).isEqualTo(IngredientStatus.ACTIVE);
        }

        @Test
        @DisplayName("deve criar ingrediente com status informado no request (override do default)")
        void shouldCreateWithRequestedStatus() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .status(IngredientStatus.INACTIVE)
                    .build();

            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, req);

            then(ingredientRepository).should().save(argThat(i -> i.getStatus() == IngredientStatus.INACTIVE));
        }

        @Test
        @DisplayName("deve persistir campos de stock no create quando informados")
        void shouldPersistStockFieldsOnCreate() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .stockQuantity(new BigDecimal("12.50"))
                    .lowStockThreshold(new BigDecimal("2.00"))
                    .build();

            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, req);

            then(ingredientRepository).should().save(argThat(i ->
                    new BigDecimal("12.50").compareTo(i.getStockQuantity()) == 0
                            && new BigDecimal("2.00").compareTo(i.getLowStockThreshold()) == 0));
        }

        @Test
        @DisplayName("deve preencher createdAt no create")
        void shouldSetCreatedAtOnCreate() {
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, ingredientRequest);

            then(ingredientRepository).should().save(argThat(i -> i.getCreatedAt() != null));
        }

        @Test
        @DisplayName("deve persistir salePrice no create quando informado")
        void shouldPersistSalePriceOnCreate() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .salePrice(new BigDecimal("8.50"))
                    .build();

            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, req);

            then(ingredientRepository).should().save(argThat(i ->
                    new BigDecimal("8.50").compareTo(i.getSalePrice()) == 0));
        }

        @Test
        @DisplayName("deve lançar DuplicateIngredientException quando nome já está cadastrado")
        void shouldThrowWhenNameAlreadyExists() {
            given(ingredientRepository.existsByNameAndMerchantId(ingredientRequest.getName(), merchantId)).willReturn(true);

            assertThatThrownBy(() -> ingredientService.create(merchantId, ingredientRequest))
                    .isInstanceOf(DuplicateIngredientException.class)
                    .hasMessageContaining("nome");

            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }

        @Test
        @DisplayName("deve lançar DuplicateIngredientException quando canonical name já existe (variação de caixa/acento/espaço)")
        void shouldThrowWhenCanonicalNameAlreadyExists() {
            IngredientRequest variant = IngredientRequest.builder()
                    .name("FARINHA  DE TRIGO ")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .build();
            given(ingredientRepository.existsByNameAndMerchantId("FARINHA  DE TRIGO ", merchantId)).willReturn(false);
            given(ingredientRepository.existsByCanonicalNameAndMerchantId("farinha de trigo", merchantId)).willReturn(true);

            assertThatThrownBy(() -> ingredientService.create(merchantId, variant))
                    .isInstanceOf(DuplicateIngredientException.class)
                    .hasMessageContaining("nome");

            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }

        @Test
        @DisplayName("deve popular canonicalName normalizado a partir do nome (lowercase, sem acentos)")
        void shouldPopulateCanonicalNameNormalizedFromName() {
            IngredientRequest withAccent = IngredientRequest.builder()
                    .name("Açaí Premium").unit("ml")
                    .costPerUnit(new BigDecimal("0.05"))
                    .build();
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, withAccent);

            then(ingredientRepository).should()
                    .save(argThat(i -> "acai premium".equals(i.getCanonicalName())));
        }

        @Test
        @DisplayName("deve apagar as notificações 'MISSING_INGREDIENT' do canonical name após salvar")
        void shouldDeleteMissingIngredientNotificationsAfterSave() {
            IngredientRequest withAccent = IngredientRequest.builder()
                    .name("Açaí Premium").unit("ml")
                    .costPerUnit(new BigDecimal("0.05"))
                    .build();
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.save(any(Ingredient.class)))
                    .willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, withAccent);

            then(notificationService).should().deleteMissingIngredient("acai premium", merchantId);
        }

        @Test
        @DisplayName("deve atribuir position = max(position)+1 do merchant no create")
        void shouldAssignNextPositionOnCreate() {
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.findMaxPositionByMerchantId(merchantId)).willReturn(4);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, ingredientRequest);

            then(ingredientRepository).should().save(argThat(i -> i.getPosition() != null && i.getPosition() == 5));
        }

        @Test
        @DisplayName("deve atribuir position = 0 ao primeiro ingrediente do merchant (max nulo)")
        void shouldAssignZeroPositionForFirstIngredient() {
            given(ingredientRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(ingredientRepository.findMaxPositionByMerchantId(merchantId)).willReturn(null);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.create(merchantId, ingredientRequest);

            then(ingredientRepository).should().save(argThat(i -> i.getPosition() != null && i.getPosition() == 0));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve computar totalStockCost como stockQuantity × costPerUnit")
        void shouldComputeTotalStockCost() {
            ingredient.setStockQuantity(new BigDecimal("10.00"));
            ingredient.setCostPerUnit(new BigDecimal("3.50"));

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));

            IngredientResponse result = ingredientService.findById(merchantId, ingredientId);

            assertThat(result.getTotalStockCost()).isEqualByComparingTo(new BigDecimal("35.00"));
        }

        @Test
        @DisplayName("deve retornar IngredientResponse quando ingrediente existe")
        void shouldReturnResponseWhenExists() {
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));

            IngredientResponse result = ingredientService.findById(merchantId, ingredientId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(ingredientId);
            assertThat(result.getName()).isEqualTo(ingredient.getName());
            assertThat(result.getUnit()).isEqualTo(ingredient.getUnit());
            assertThat(result.getCostPerUnit()).isEqualByComparingTo(ingredient.getCostPerUnit());
            assertThat(result.getDefaultQuantity()).isEqualByComparingTo(ingredient.getDefaultQuantity());
        }

        @Test
        @DisplayName("deve mapear createdAt da entidade para a response")
        void shouldMapCreatedAt() {
            java.time.LocalDateTime createdAt = java.time.LocalDateTime.of(2026, 1, 5, 10, 30);
            ingredient.setCreatedAt(createdAt);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));

            IngredientResponse result = ingredientService.findById(merchantId, ingredientId);

            assertThat(result.getCreatedAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("deve mapear createdAt nulo (linhas legadas) para a response")
        void shouldMapNullCreatedAt() {
            ingredient.setCreatedAt(null);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));

            IngredientResponse result = ingredientService.findById(merchantId, ingredientId);

            assertThat(result.getCreatedAt()).isNull();
        }

        @Test
        @DisplayName("deve lançar IngredientNotFoundException quando ingrediente não existe")
        void shouldThrowWhenIngredientNotFound() {
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> ingredientService.findById(merchantId, ingredientId))
                    .isInstanceOf(IngredientNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de ingredientes filtrada por nome (contains, case-insensitive)")
        void shouldReturnPagedIngredientsFilteredByName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "bac", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(ingredient), pageable, 1));

            org.springframework.data.domain.Page<IngredientResponse> result =
                    ingredientService.findAll(merchantId, "bac", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getId()).isEqualTo(ingredientId);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve popular usageCount via query agregada em batch")
        void shouldPopulateUsageCountInBatch() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(ingredient), pageable, 1));
            given(includeRepository.countByLowercaseNameInForMerchant(eq(merchantId), any()))
                    .willReturn(List.<Object[]>of(new Object[]{"farinha de trigo", 4L}));

            org.springframework.data.domain.Page<IngredientResponse> result =
                    ingredientService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getUsageCount()).isEqualTo(4L);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(ingredientRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

            org.springframework.data.domain.Page<IngredientResponse> result =
                    ingredientService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar ingrediente existente e retornar IngredientResponse atualizado")
        void shouldUpdateAndReturnUpdatedResponse() {
            IngredientRequest updateRequest = IngredientRequest.builder()
                    .name("Farinha de Trigo Integral")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("5.75"))
                    .defaultQuantity(new BigDecimal("0.35"))
                    .build();

            Ingredient updatedIngredient = Ingredient.builder()
                    .id(ingredientId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Farinha de Trigo Integral")
                    .canonicalName("farinha de trigo integral")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("5.75"))
                    .defaultQuantity(new BigDecimal("0.35"))
                    .status(IngredientStatus.ACTIVE)
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willReturn(updatedIngredient);

            IngredientResponse result = ingredientService.update(merchantId, ingredientId, updateRequest);

            assertThat(result.getName()).isEqualTo("Farinha de Trigo Integral");
            assertThat(result.getCostPerUnit()).isEqualByComparingTo(new BigDecimal("5.75"));
            assertThat(result.getDefaultQuantity()).isEqualByComparingTo(new BigDecimal("0.35"));
        }

        @Test
        @DisplayName("deve recomputar canonicalName ao alterar nome")
        void shouldRecomputeCanonicalNameOnUpdate() {
            IngredientRequest updateRequest = IngredientRequest.builder()
                    .name("PISTACHE")
                    .unit("g")
                    .costPerUnit(new BigDecimal("1.20"))
                    .build();
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.update(merchantId, ingredientId, updateRequest);

            then(ingredientRepository).should()
                    .save(argThat(i -> "pistache".equals(i.getCanonicalName())));
        }

        @Test
        @DisplayName("deve atualizar status quando informado no request")
        void shouldUpdateStatusWhenProvided() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .status(IngredientStatus.INACTIVE)
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.update(merchantId, ingredientId, req);

            then(ingredientRepository).should().save(argThat(i -> i.getStatus() == IngredientStatus.INACTIVE));
        }

        @Test
        @DisplayName("não deve mudar status quando request.status é null")
        void shouldKeepStatusWhenRequestStatusIsNull() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .status(null)
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.update(merchantId, ingredientId, req);

            then(ingredientRepository).should().save(argThat(i -> i.getStatus() == IngredientStatus.ACTIVE));
        }

        @Test
        @DisplayName("deve persistir salePrice quando informado")
        void shouldPersistSalePriceWhenProvided() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .salePrice(new BigDecimal("9.99"))
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.update(merchantId, ingredientId, req);

            then(ingredientRepository).should().save(argThat(i ->
                    new BigDecimal("9.99").compareTo(i.getSalePrice()) == 0));
        }

        @Test
        @DisplayName("deve lançar DuplicateIngredientException ao renomear para canonical de outro ingrediente")
        void shouldThrowWhenRenamingToCanonicalOfAnotherIngredient() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("MORANGO")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.10"))
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.existsByCanonicalNameAndMerchantIdAndIdNot("morango", merchantId, ingredientId))
                    .willReturn(true);

            assertThatThrownBy(() -> ingredientService.update(merchantId, ingredientId, req))
                    .isInstanceOf(DuplicateIngredientException.class)
                    .hasMessageContaining("nome");

            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }

        @Test
        @DisplayName("deve permitir update mantendo o próprio nome (exclui o próprio id da checagem de duplicidade)")
        void shouldAllowUpdateKeepingOwnName() {
            IngredientRequest req = IngredientRequest.builder()
                    .name("Farinha de Trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .build();

            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ingredient));
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            assertThatNoException().isThrownBy(() -> ingredientService.update(merchantId, ingredientId, req));

            then(ingredientRepository).should()
                    .existsByCanonicalNameAndMerchantIdAndIdNot("farinha de trigo", merchantId, ingredientId);
        }

        @Test
        @DisplayName("deve lançar IngredientNotFoundException ao atualizar ingrediente inexistente")
        void shouldThrowWhenIngredientNotFoundForUpdate() {
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> ingredientService.update(merchantId, ingredientId, ingredientRequest))
                    .isInstanceOf(IngredientNotFoundException.class);

            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar ingrediente existente sem lançar exceção")
        void shouldDeleteExistingIngredient() {
            given(ingredientRepository.existsByIdAndMerchantId(ingredientId, merchantId)).willReturn(true);
            willDoNothing().given(ingredientRepository).deleteByIdAndMerchantId(ingredientId, merchantId);

            assertThatNoException().isThrownBy(() -> ingredientService.delete(merchantId, ingredientId));

            then(ingredientRepository).should().deleteByIdAndMerchantId(ingredientId, merchantId);
        }

        @Test
        @DisplayName("deve lançar IngredientNotFoundException ao deletar ingrediente inexistente")
        void shouldThrowWhenIngredientNotFoundForDelete() {
            given(ingredientRepository.existsByIdAndMerchantId(ingredientId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> ingredientService.delete(merchantId, ingredientId))
                    .isInstanceOf(IngredientNotFoundException.class);

            then(ingredientRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }

    @Nested
    @DisplayName("reorder()")
    class Reorder {

        private Ingredient ingredientAt(int position) {
            return Ingredient.builder()
                    .id(ingredientId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Farinha de Trigo")
                    .canonicalName("farinha de trigo")
                    .unit("kg")
                    .costPerUnit(new BigDecimal("4.50"))
                    .status(IngredientStatus.ACTIVE)
                    .position(position)
                    .build();
        }

        @Test
        @DisplayName("mover para baixo desloca o intervalo para a esquerda e grava a nova posição")
        void shouldMoveDown() {
            Ingredient ing = ingredientAt(2);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(10L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 5);

            then(ingredientRepository).should().shiftRangeLeft(merchantId, 2, 5);
            then(ingredientRepository).should(never()).shiftRangeRight(any(), anyInt(), anyInt());
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 5));
        }

        @Test
        @DisplayName("mover para cima desloca o intervalo para a direita e grava a nova posição")
        void shouldMoveUp() {
            Ingredient ing = ingredientAt(5);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(10L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 1);

            then(ingredientRepository).should().shiftRangeRight(merchantId, 5, 1);
            then(ingredientRepository).should(never()).shiftRangeLeft(any(), anyInt(), anyInt());
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 1));
        }

        @Test
        @DisplayName("mover para a mesma posição é no-op (não desloca nem grava)")
        void shouldBeNoOpWhenSamePosition() {
            Ingredient ing = ingredientAt(3);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(10L);

            ingredientService.reorder(merchantId, ingredientId, 3);

            then(ingredientRepository).should(never()).shiftRangeLeft(any(), anyInt(), anyInt());
            then(ingredientRepository).should(never()).shiftRangeRight(any(), anyInt(), anyInt());
            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }

        @Test
        @DisplayName("mover para o início da lista (posição 0)")
        void shouldMoveToFirst() {
            Ingredient ing = ingredientAt(7);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(10L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 0);

            then(ingredientRepository).should().shiftRangeRight(merchantId, 7, 0);
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 0));
        }

        @Test
        @DisplayName("posição acima do total é limitada ao último índice (count-1)")
        void shouldClampToLastIndex() {
            Ingredient ing = ingredientAt(2);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(10L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 999);

            then(ingredientRepository).should().shiftRangeLeft(merchantId, 2, 9);
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 9));
        }

        @Test
        @DisplayName("posição para o início da próxima página (page-size aware) usa o índice global recebido")
        void shouldMoveToStartOfNextPage() {
            // 40 rows across 2 pages of 20; item at global 5 (page 0) → start of page 1 = global 20
            Ingredient ing = ingredientAt(5);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(40L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 20);

            then(ingredientRepository).should().shiftRangeLeft(merchantId, 5, 20);
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 20));
        }

        @Test
        @DisplayName("posição para o fim da página anterior (page-size aware) usa o índice global recebido")
        void shouldMoveToEndOfPreviousPage() {
            // item at global 25 (page 1) → end of page 0 = global 19
            Ingredient ing = ingredientAt(25);
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.of(ing));
            given(ingredientRepository.countByMerchantId(merchantId)).willReturn(40L);
            given(ingredientRepository.save(any(Ingredient.class))).willAnswer(inv -> inv.getArgument(0));

            ingredientService.reorder(merchantId, ingredientId, 19);

            then(ingredientRepository).should().shiftRangeRight(merchantId, 25, 19);
            then(ingredientRepository).should().save(argThat(i -> i.getPosition() == 19));
        }

        @Test
        @DisplayName("é escopado por merchant: ingrediente de outro merchant não é encontrado")
        void shouldBeMerchantScoped() {
            given(ingredientRepository.findByIdAndMerchantId(ingredientId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> ingredientService.reorder(merchantId, ingredientId, 3))
                    .isInstanceOf(IngredientNotFoundException.class);

            then(ingredientRepository).should(never()).shiftRangeLeft(any(), anyInt(), anyInt());
            then(ingredientRepository).should(never()).shiftRangeRight(any(), anyInt(), anyInt());
            then(ingredientRepository).should(never()).save(any(Ingredient.class));
        }
    }
}
