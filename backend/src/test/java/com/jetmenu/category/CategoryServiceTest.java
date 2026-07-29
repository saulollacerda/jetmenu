package com.jetmenu.category;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.product.ProductRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("CategoryService")
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CategoryService categoryService;

    private UUID merchantId;
    private UUID categoryId;
    private Category category;
    private CategoryRequest categoryRequest;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        categoryRequest = CategoryRequest.builder()
                .name("Lanches")
                .build();

        category = Category.builder()
                .id(categoryId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Lanches")
                .build();

        lenient().when(merchantRepository.getReferenceById(any()))
                .thenReturn(Merchant.builder().id(merchantId).build());
    }

    // -------------------------------------------------------------------------
    // create()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar categoria com dados válidos e retornar CategoryResponse")
        void shouldCreateCategoryAndReturnResponse() {
            given(categoryRepository.existsByNameAndMerchantId(categoryRequest.getName(), merchantId)).willReturn(false);
            given(categoryRepository.save(any(Category.class))).willReturn(category);

            CategoryResponse result = categoryService.create(merchantId, categoryRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(categoryId);
            assertThat(result.getName()).isEqualTo(categoryRequest.getName());
            then(categoryRepository).should().save(argThat(c -> merchantId.equals(c.getMerchant().getId())));
        }

        @Test
        @DisplayName("deve criar categoria com origin JETMENU e expor origin na resposta")
        void shouldCreateCategoryWithJetmenuOrigin() {
            given(categoryRepository.existsByNameAndMerchantId(categoryRequest.getName(), merchantId)).willReturn(false);
            given(categoryRepository.save(any(Category.class))).willAnswer(inv -> inv.getArgument(0));

            CategoryResponse result = categoryService.create(merchantId, categoryRequest);

            assertThat(result.getOrigin()).isEqualTo(CatalogOrigin.JETMENU);
            then(categoryRepository).should().save(argThat(c -> c.getOrigin() == CatalogOrigin.JETMENU));
        }

        @Test
        @DisplayName("deve lançar DuplicateCategoryException quando nome já está cadastrado")
        void shouldThrowWhenNameAlreadyExists() {
            given(categoryRepository.existsByNameAndMerchantId(categoryRequest.getName(), merchantId)).willReturn(true);

            assertThatThrownBy(() -> categoryService.create(merchantId, categoryRequest))
                    .isInstanceOf(DuplicateCategoryException.class)
                    .hasMessageContaining("nome");

            then(categoryRepository).should(never()).save(any(Category.class));
        }
    }

    // -------------------------------------------------------------------------
    // findById()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar CategoryResponse quando categoria existe")
        void shouldReturnResponseWhenExists() {
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));

            CategoryResponse result = categoryService.findById(merchantId, categoryId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(categoryId);
            assertThat(result.getName()).isEqualTo(category.getName());
        }

        @Test
        @DisplayName("deve lançar CategoryNotFoundException quando categoria não existe")
        void shouldThrowWhenCategoryNotFound() {
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.findById(merchantId, categoryId))
                    .isInstanceOf(CategoryNotFoundException.class);
        }

        @Test
        @DisplayName("deve retornar productCount da categoria")
        void shouldReturnProductCount() {
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.countByCategoryIdAndMerchantId(categoryId, merchantId)).willReturn(7L);

            CategoryResponse result = categoryService.findById(merchantId, categoryId);

            assertThat(result.getProductCount()).isEqualTo(7L);
        }
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de categorias filtrada por nome (contains, case-insensitive)")
        void shouldReturnPagedCategoriesFilteredByName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(categoryRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "lan", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(category), pageable, 1));

            org.springframework.data.domain.Page<CategoryResponse> result =
                    categoryService.findAll(merchantId, "lan", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("Lanches");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(categoryRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

            org.springframework.data.domain.Page<CategoryResponse> result =
                    categoryService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // update()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar categoria existente e retornar CategoryResponse atualizado")
        void shouldUpdateAndReturnUpdatedResponse() {
            CategoryRequest updateRequest = CategoryRequest.builder()
                    .name("Bebidas")
                    .build();

            Category updatedCategory = Category.builder()
                    .id(categoryId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Bebidas")
                    .build();

            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(categoryRepository.save(any(Category.class))).willReturn(updatedCategory);

            CategoryResponse result = categoryService.update(merchantId, categoryId, updateRequest);

            assertThat(result.getName()).isEqualTo("Bebidas");
            assertThat(result.getId()).isEqualTo(categoryId);
        }

        @Test
        @DisplayName("deve lançar CategoryNotFoundException ao atualizar categoria inexistente")
        void shouldThrowWhenCategoryNotFoundForUpdate() {
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(merchantId, categoryId, categoryRequest))
                    .isInstanceOf(CategoryNotFoundException.class);

            then(categoryRepository).should(never()).save(any(Category.class));
        }
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar categoria existente sem lançar exceção")
        void shouldDeleteExistingCategory() {
            given(categoryRepository.existsByIdAndMerchantId(categoryId, merchantId)).willReturn(true);
            willDoNothing().given(categoryRepository).deleteByIdAndMerchantId(categoryId, merchantId);

            assertThatNoException().isThrownBy(() -> categoryService.delete(merchantId, categoryId));

            then(categoryRepository).should().deleteByIdAndMerchantId(categoryId, merchantId);
        }

        @Test
        @DisplayName("deve lançar CategoryNotFoundException ao deletar categoria inexistente")
        void shouldThrowWhenCategoryNotFoundForDelete() {
            given(categoryRepository.existsByIdAndMerchantId(categoryId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> categoryService.delete(merchantId, categoryId))
                    .isInstanceOf(CategoryNotFoundException.class);

            then(categoryRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }
}

