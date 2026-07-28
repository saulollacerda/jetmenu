package com.jetmenu.product;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryNotFoundException;
import com.jetmenu.category.CategoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MerchantRepository merchantRepository;


    @InjectMocks
    private ProductService productService;

    private UUID merchantId;
    private UUID productId;
    private UUID categoryId;
    private Category category;
    private Product product;
    private ProductRequest productRequest;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        lenient().when(merchantRepository.getReferenceById(any())).thenReturn(Merchant.builder().id(merchantId).build());
        productId = UUID.randomUUID();
        categoryId = UUID.randomUUID();

        category = Category.builder()
                .id(categoryId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("Lanches")
                .build();

        productRequest = ProductRequest.builder()
                .name("X-Burguer")
                .price(new BigDecimal("25.90"))
                .categoryId(categoryId)
                .build();

        product = Product.builder()
                .id(productId)
                .merchant(Merchant.builder().id(merchantId).build())
                .name("X-Burguer")
                .price(new BigDecimal("25.90"))
                .status(ProductStatus.ACTIVE)
                .category(category)
                .build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("deve criar produto com dados válidos e retornar ProductResponse")
        void shouldCreateProductAndReturnResponse() {
            given(productRepository.existsByNameAndMerchantId(productRequest.getName(), merchantId)).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willReturn(product);

            ProductResponse result = productService.create(merchantId, productRequest);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(productId);
            assertThat(result.getName()).isEqualTo(productRequest.getName());
            assertThat(result.getPrice()).isEqualByComparingTo(productRequest.getPrice());
            then(productRepository).should().save(argThat(p -> merchantId.equals(p.getMerchant().getId())));
        }

        @Test
        @DisplayName("deve criar produto com origin JETMENU e expor origin na resposta")
        void shouldCreateProductWithJetmenuOrigin() {
            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            ProductResponse result = productService.create(merchantId, productRequest);

            assertThat(result.getOrigin()).isEqualTo(CatalogOrigin.JETMENU);
            then(productRepository).should().save(argThat(p -> p.getOrigin() == CatalogOrigin.JETMENU));
        }

        @Test
        @DisplayName("deve criar produto com status ACTIVE por padrão")
        void shouldCreateProductWithActiveStatusByDefault() {
            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willReturn(product);

            ProductResponse result = productService.create(merchantId, productRequest);

            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("deve atribuir a categoria ao produto e retornar categoryId/categoryName na resposta")
        void shouldAssignCategoryAndReturnCategoryFieldsInResponse() {
            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willReturn(product);

            ProductResponse result = productService.create(merchantId, productRequest);

            assertThat(result.getCategoryId()).isEqualTo(categoryId);
            assertThat(result.getCategoryName()).isEqualTo("Lanches");
            then(productRepository).should().save(argThat(p -> p.getCategory() != null
                    && categoryId.equals(p.getCategory().getId())));
        }

        @Test
        @DisplayName("deve lançar CategoryNotFoundException quando categoria não pertence ao owner")
        void shouldThrowWhenCategoryNotFoundForOwner() {
            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.create(merchantId, productRequest))
                    .isInstanceOf(CategoryNotFoundException.class);

            then(productRepository).should(never()).save(any(Product.class));
        }

        @Test
        @DisplayName("deve criar produto com status informado no request (override do default)")
        void shouldCreateWithRequestedStatus() {
            ProductRequest requestWithInactive = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .categoryId(categoryId)
                    .status(ProductStatus.INACTIVE)
                    .build();

            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            productService.create(merchantId, requestWithInactive);

            then(productRepository).should().save(argThat(p -> p.getStatus() == ProductStatus.INACTIVE));
        }

        @Test
        @DisplayName("deve lançar DuplicateProductException quando nome já está cadastrado")
        void shouldThrowWhenNameAlreadyExists() {
            given(productRepository.existsByNameAndMerchantId(productRequest.getName(), merchantId)).willReturn(true);

            assertThatThrownBy(() -> productService.create(merchantId, productRequest))
                    .isInstanceOf(DuplicateProductException.class)
                    .hasMessageContaining("nome");

            then(productRepository).should(never()).save(any(Product.class));
        }

        @Test
        @DisplayName("deve popular canonicalName normalizado a partir do nome (lowercase, sem acentos, espaços colapsados)")
        void shouldPopulateCanonicalNameNormalizedFromName() {
            ProductRequest withAccent = ProductRequest.builder()
                    .name("  Pão de   Açúcar ")
                    .price(new BigDecimal("12.00"))
                    .categoryId(categoryId)
                    .build();
            given(productRepository.existsByNameAndMerchantId(anyString(), eq(merchantId))).willReturn(false);
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            productService.create(merchantId, withAccent);

            then(productRepository).should()
                    .save(argThat(p -> "pao de acucar".equals(p.getCanonicalName())));
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("deve retornar ProductResponse quando produto existe")
        void shouldReturnResponseWhenExists() {
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(productId);
            assertThat(result.getName()).isEqualTo(product.getName());
            assertThat(result.getPrice()).isEqualByComparingTo(product.getPrice());
            assertThat(result.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        }

        @Test
        @DisplayName("deve lançar ProductNotFoundException quando produto não existe")
        void shouldThrowWhenProductNotFound() {
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.findById(merchantId, productId))
                    .isInstanceOf(ProductNotFoundException.class);
        }

        @Test
        @DisplayName("deve retornar unitCost como soma de cost x quantity dos includes")
        void shouldReturnUnitCostFromIncludes() {
            Include i1 = Include.builder().cost(new BigDecimal("2.00")).quantity(new BigDecimal("3")).build();
            Include i2 = Include.builder().cost(new BigDecimal("1.50")).quantity(new BigDecimal("2")).build();
            product.setIncludes(List.of(i1, i2));

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            // 2.00 * 3 + 1.50 * 2 = 9.00
            assertThat(result.getUnitCost()).isEqualByComparingTo(new BigDecimal("9.00"));
        }

        @Test
        @DisplayName("deve retornar unitCost zero quando produto não tem includes")
        void shouldReturnZeroUnitCostWhenNoIncludes() {
            product.setIncludes(List.of());

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            assertThat(result.getUnitCost()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("deve calcular marginPct como (price - unitCost) / price x 100")
        void shouldCalculateMarginPct() {
            Include i1 = Include.builder().cost(new BigDecimal("10.00")).quantity(new BigDecimal("1")).build();
            product.setIncludes(List.of(i1));
            product.setPrice(new BigDecimal("25.00"));

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            // (25 - 10) / 25 * 100 = 60.00
            assertThat(result.getMarginPct()).isEqualByComparingTo(new BigDecimal("60.00"));
        }

        @Test
        @DisplayName("deve retornar marginPct null quando price é zero")
        void shouldReturnNullMarginWhenPriceIsZero() {
            product.setPrice(BigDecimal.ZERO);
            product.setIncludes(List.of());

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            assertThat(result.getMarginPct()).isNull();
        }

        @Test
        @DisplayName("deve retornar marginPct 100 quando unitCost é zero e price > 0")
        void shouldReturnFullMarginWhenNoCost() {
            product.setIncludes(List.of());
            product.setPrice(new BigDecimal("10.00"));

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));

            ProductResponse result = productService.findById(merchantId, productId);

            assertThat(result.getMarginPct()).isEqualByComparingTo(new BigDecimal("100.00"));
        }
    }

    @Nested
    @DisplayName("findAll(search, pageable)")
    class FindAll {

        @Test
        @DisplayName("deve retornar página de produtos filtrada por nome (contains, case-insensitive)")
        void shouldReturnPagedProductsFilteredByName() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(productRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "burg", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(product), pageable, 1));

            org.springframework.data.domain.Page<ProductResponse> result =
                    productService.findAll(merchantId, "burg", pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("X-Burguer");
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("deve tratar search nulo como string vazia (retorna tudo)")
        void shouldTreatNullSearchAsEmpty() {
            org.springframework.data.domain.Pageable pageable =
                    org.springframework.data.domain.PageRequest.of(0, 20);
            given(productRepository.findAllByMerchantIdAndNameContainingIgnoreCase(merchantId, "", pageable))
                    .willReturn(new org.springframework.data.domain.PageImpl<>(List.of(product), pageable, 1));

            org.springframework.data.domain.Page<ProductResponse> result =
                    productService.findAll(merchantId, null, pageable);

            assertThat(result.getContent()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("deve atualizar produto existente e retornar ProductResponse atualizado")
        void shouldUpdateAndReturnUpdatedResponse() {
            ProductRequest updateRequest = ProductRequest.builder()
                    .name("X-Salada")
                    .price(new BigDecimal("29.90"))
                    .categoryId(categoryId)
                    .build();

            Product updatedProduct = Product.builder()
                    .id(productId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("X-Salada")
                    .price(new BigDecimal("29.90"))
                    .status(ProductStatus.ACTIVE)
                    .category(category)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willReturn(updatedProduct);

            ProductResponse result = productService.update(merchantId, productId, updateRequest);

            assertThat(result.getName()).isEqualTo("X-Salada");
            assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("29.90"));
        }

        @Test
        @DisplayName("deve permitir trocar a categoria do produto")
        void shouldAllowChangingCategory() {
            UUID newCategoryId = UUID.randomUUID();
            Category newCategory = Category.builder()
                    .id(newCategoryId)
                    .merchant(Merchant.builder().id(merchantId).build())
                    .name("Bebidas")
                    .build();

            ProductRequest updateRequest = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .categoryId(newCategoryId)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(newCategoryId, merchantId)).willReturn(Optional.of(newCategory));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            ProductResponse result = productService.update(merchantId, productId, updateRequest);

            assertThat(result.getCategoryId()).isEqualTo(newCategoryId);
            assertThat(result.getCategoryName()).isEqualTo("Bebidas");
            then(productRepository).should().save(argThat(p ->
                    p.getCategory() != null && newCategoryId.equals(p.getCategory().getId())));
        }

        @Test
        @DisplayName("deve atualizar o status quando informado no request")
        void shouldUpdateStatusWhenProvided() {
            ProductRequest req = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .categoryId(categoryId)
                    .status(ProductStatus.INACTIVE)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            productService.update(merchantId, productId, req);

            then(productRepository).should().save(argThat(p -> p.getStatus() == ProductStatus.INACTIVE));
        }

        @Test
        @DisplayName("não deve mudar status quando request.status é null")
        void shouldKeepStatusWhenRequestStatusIsNull() {
            ProductRequest req = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .categoryId(categoryId)
                    .status(null)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            productService.update(merchantId, productId, req);

            then(productRepository).should().save(argThat(p -> p.getStatus() == ProductStatus.ACTIVE));
        }

        @Test
        @DisplayName("deve lançar CategoryNotFoundException quando categoria no update não pertence ao owner")
        void shouldThrowWhenCategoryNotFoundOnUpdate() {
            ProductRequest updateRequest = ProductRequest.builder()
                    .name("X-Burguer")
                    .price(new BigDecimal("25.90"))
                    .categoryId(categoryId)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(merchantId, productId, updateRequest))
                    .isInstanceOf(CategoryNotFoundException.class);

            then(productRepository).should(never()).save(any(Product.class));
        }

        @Test
        @DisplayName("deve atualizar canonicalName quando o nome é alterado")
        void shouldUpdateCanonicalNameWhenNameChanges() {
            ProductRequest updateRequest = ProductRequest.builder()
                    .name("CRÈME  Brûlée")
                    .price(new BigDecimal("29.90"))
                    .categoryId(categoryId)
                    .build();

            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.of(product));
            given(categoryRepository.findByIdAndMerchantId(categoryId, merchantId)).willReturn(Optional.of(category));
            given(productRepository.save(any(Product.class))).willAnswer(inv -> inv.getArgument(0));

            productService.update(merchantId, productId, updateRequest);

            then(productRepository).should()
                    .save(argThat(p -> "creme brulee".equals(p.getCanonicalName())));
        }

        @Test
        @DisplayName("deve lançar ProductNotFoundException ao atualizar produto inexistente")
        void shouldThrowWhenProductNotFoundForUpdate() {
            given(productRepository.findByIdAndMerchantId(productId, merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> productService.update(merchantId, productId, productRequest))
                    .isInstanceOf(ProductNotFoundException.class);

            then(productRepository).should(never()).save(any(Product.class));
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("deve deletar produto existente sem lançar exceção")
        void shouldDeleteExistingProduct() {
            given(productRepository.existsByIdAndMerchantId(productId, merchantId)).willReturn(true);
            willDoNothing().given(productRepository).deleteByIdAndMerchantId(productId, merchantId);

            assertThatNoException().isThrownBy(() -> productService.delete(merchantId, productId));

            then(productRepository).should().deleteByIdAndMerchantId(productId, merchantId);
        }

        @Test
        @DisplayName("deve lançar ProductNotFoundException ao deletar produto inexistente")
        void shouldThrowWhenProductNotFoundForDelete() {
            given(productRepository.existsByIdAndMerchantId(productId, merchantId)).willReturn(false);

            assertThatThrownBy(() -> productService.delete(merchantId, productId))
                    .isInstanceOf(ProductNotFoundException.class);

            then(productRepository).should(never()).deleteByIdAndMerchantId(any(), any());
        }
    }
}
