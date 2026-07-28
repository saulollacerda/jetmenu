package com.jetmenu.product;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProductService — integração com Postgres")
class ProductServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private ProductService productService;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Merchant merchant;
    private Category category;

    @BeforeEach
    void setup() {
        merchant = createMerchantAndAuthenticate();
        category = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
    }

    @Test
    @DisplayName("create deve persistir produto com FK para merchant e category")
    void create_shouldPersistProductWithFks() {
        ProductRequest request = ProductRequest.builder()
                .name("Açaí 500ml").price(new BigDecimal("20.00"))
                .categoryId(category.getId()).build();

        ProductResponse response = productService.create(merchant.getId(), request);

        assertThat(response.getId()).isNotNull();
        Product persisted = productRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(persisted.getCategory().getId()).isEqualTo(category.getId());
        assertThat(persisted.getStatus()).isEqualTo(ProductStatus.ACTIVE);
    }

    @Test
    @DisplayName("create deve rejeitar categoria de outro merchant")
    void create_shouldRejectCategoryFromAnotherMerchant() {
        Merchant other = createMerchant("Outro");
        Category otherCategory = categoryRepository.save(Category.builder()
                .merchant(other).name("X").build());

        ProductRequest request = ProductRequest.builder()
                .name("Açaí").price(new BigDecimal("10")).categoryId(otherCategory.getId()).build();

        assertThatThrownBy(() -> productService.create(merchant.getId(), request))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("update deve modificar nome e preço sem afetar merchant ownership")
    void update_shouldModifyProduct() {
        ProductResponse created = productService.create(merchant.getId(), ProductRequest.builder()
                .name("Velho").price(new BigDecimal("10")).categoryId(category.getId()).build());

        productService.update(merchant.getId(), created.getId(), ProductRequest.builder()
                .name("Novo").price(new BigDecimal("15"))
                .categoryId(category.getId()).build());

        Product persisted = productRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Novo");
        assertThat(persisted.getPrice()).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("delete deve remover produto do banco")
    void delete_shouldRemoveProduct() {
        ProductResponse created = productService.create(merchant.getId(), ProductRequest.builder()
                .name("X").price(new BigDecimal("5")).categoryId(category.getId()).build());

        productService.delete(merchant.getId(), created.getId());

        assertThat(productRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAll deve paginar produtos do merchant filtrando por nome")
    void findAll_shouldPaginateWithSearch() {
        productService.create(merchant.getId(), ProductRequest.builder()
                .name("Açaí 330ml").price(new BigDecimal("15")).categoryId(category.getId()).build());
        productService.create(merchant.getId(), ProductRequest.builder()
                .name("Açaí 500ml").price(new BigDecimal("20")).categoryId(category.getId()).build());
        productService.create(merchant.getId(), ProductRequest.builder()
                .name("Refrigerante").price(new BigDecimal("8")).categoryId(category.getId()).build());

        var page = productService.findAll(merchant.getId(), "Açaí", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("findAll NÃO deve listar produtos de outro merchant (isolamento)")
    void findAll_shouldIsolateBetweenMerchants() {
        productService.create(merchant.getId(), ProductRequest.builder()
                .name("Meu").price(new BigDecimal("10")).categoryId(category.getId()).build());

        Merchant other = createMerchant("Outro");
        Category otherCat = categoryRepository.save(Category.builder()
                .merchant(other).name("Outra").build());
        productService.create(other.getId(), ProductRequest.builder()
                .name("Do outro").price(new BigDecimal("99")).categoryId(otherCat.getId()).build());

        var page = productService.findAll(other.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Do outro");
    }
}
