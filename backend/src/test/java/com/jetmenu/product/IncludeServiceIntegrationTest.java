package com.jetmenu.product;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("IncludeService — integração com Postgres")
class IncludeServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private IncludeService includeService;
    @Autowired private IncludeRepository includeRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @PersistenceContext private EntityManager entityManager;

    private Merchant merchant;
    private Product product;

    @BeforeEach
    void setup() {
        merchant = createMerchantAndAuthenticate();
        Category cat = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        product = productRepository.save(Product.builder()
                .merchant(merchant).name("Açaí 500ml").price(new BigDecimal("20"))
                .status(ProductStatus.ACTIVE).category(cat).build());
    }

    @Test
    @DisplayName("add deve persistir Include ligado ao produto")
    void add_shouldPersistInclude() {
        IncludeRequest request = IncludeRequest.builder()
                .name("copo").cost(new BigDecimal("0.50")).quantity(BigDecimal.ONE).build();

        IncludeResponse response = includeService.add(merchant.getId(), product.getId(), request);

        assertThat(response.getId()).isNotNull();
        Include persisted = includeRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getProduct().getId()).isEqualTo(product.getId());
        assertThat(persisted.getName()).isEqualTo("copo");
    }

    @Test
    @DisplayName("addBatch deve persistir vários Includes de uma vez")
    void addBatch_shouldPersistMultiple() {
        List<IncludeRequest> requests = List.of(
                IncludeRequest.builder().name("copo").cost(new BigDecimal("0.50")).quantity(BigDecimal.ONE).build(),
                IncludeRequest.builder().name("colher").cost(new BigDecimal("0.10")).quantity(BigDecimal.ONE).build(),
                IncludeRequest.builder().name("granola").cost(new BigDecimal("0.05")).quantity(new BigDecimal("30")).build()
        );

        List<IncludeResponse> responses = includeService.addBatch(merchant.getId(), product.getId(), requests);

        assertThat(responses).hasSize(3);
        assertThat(includeRepository.findByProductIdAndProductMerchantId(product.getId(), merchant.getId())).hasSize(3);
    }

    @Test
    @DisplayName("findByProductId deve listar Includes do produto")
    void findByProductId_shouldListIncludes() {
        includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("copo").cost(new BigDecimal("0.50")).quantity(BigDecimal.ONE).build());

        var includes = includeService.findByProductId(merchant.getId(), product.getId());

        assertThat(includes).hasSize(1);
        assertThat(includes.get(0).getName()).isEqualTo("copo");
        // totalCost = 0.50 × 1
        assertThat(includes.get(0).getTotalCost()).isEqualByComparingTo("0.50");
    }

    @Test
    @DisplayName("update deve modificar campos do Include")
    void update_shouldModifyInclude() {
        IncludeResponse created = includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("velho").cost(new BigDecimal("1.00")).quantity(BigDecimal.ONE).build());

        includeService.update(merchant.getId(), product.getId(), created.getId(), IncludeRequest.builder()
                .name("novo").cost(new BigDecimal("2.00")).quantity(new BigDecimal("5")).build());

        Include persisted = includeRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("novo");
        assertThat(persisted.getCost()).isEqualByComparingTo("2.00");
        assertThat(persisted.getQuantity()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("delete deve remover o Include")
    void delete_shouldRemoveInclude() {
        IncludeResponse created = includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("x").cost(new BigDecimal("0.10")).quantity(BigDecimal.ONE).build());

        includeService.delete(merchant.getId(), product.getId(), created.getId());

        assertThat(includeRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("deleteAllByProductId deve remover todos os Includes do produto")
    void deleteAllByProductId_shouldRemoveAll() {
        includeService.addBatch(merchant.getId(), product.getId(), List.of(
                IncludeRequest.builder().name("a").cost(new BigDecimal("0.10")).quantity(BigDecimal.ONE).build(),
                IncludeRequest.builder().name("b").cost(new BigDecimal("0.20")).quantity(BigDecimal.ONE).build()
        ));

        long count = includeService.deleteAllByProductId(merchant.getId(), product.getId());

        assertThat(count).isEqualTo(2);
        assertThat(includeRepository.findByProductIdAndProductMerchantId(product.getId(), merchant.getId())).isEmpty();
    }

    @Test
    @DisplayName("REGRESSÃO: adicionar Include em sequência NÃO deve apagar os anteriores (bug relatado)")
    void add_inSequence_shouldKeepAllPreviouslyAddedIncludes() {
        // Cenário do bug: usuário adiciona ingredientes na ficha técnica um por um,
        // e os anteriores somem.
        // Em produção cada chamada REST é uma transação separada (entity carregado fresh).
        // Simulamos isso aqui com flush() + clear() entre as adds, forçando o Hibernate
        // a recarregar o Product do banco a cada add (não apenas usar a instância em cache).
        includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("leite ninho").cost(new BigDecimal("0.05"))
                .quantity(new BigDecimal("40")).build());
        entityManager.flush();
        entityManager.clear();

        includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("chocoball").cost(new BigDecimal("0.06"))
                .quantity(new BigDecimal("20")).build());
        entityManager.flush();
        entityManager.clear();

        includeService.add(merchant.getId(), product.getId(), IncludeRequest.builder()
                .name("morango").cost(new BigDecimal("0.01"))
                .quantity(new BigDecimal("1")).build());
        entityManager.flush();
        entityManager.clear();

        var persisted = includeRepository.findByProductIdAndProductMerchantId(
                product.getId(), merchant.getId());

        // Esperado: 3 Includes — todos preservados
        assertThat(persisted).hasSize(3);
        assertThat(persisted).extracting("name")
                .containsExactlyInAnyOrder("leite ninho", "chocoball", "morango");
    }

    @Test
    @DisplayName("add deve persistir kind PACKAGING quando enviado")
    void add_shouldPersistKindPackaging() {
        IncludeRequest request = IncludeRequest.builder()
                .name("copo").cost(new BigDecimal("0.50")).quantity(BigDecimal.ONE)
                .kind(IncludeKind.PACKAGING).build();

        IncludeResponse response = includeService.add(merchant.getId(), product.getId(), request);

        assertThat(response.getKind()).isEqualTo(IncludeKind.PACKAGING);
        Include persisted = includeRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getKind()).isEqualTo(IncludeKind.PACKAGING);
    }

    @Test
    @DisplayName("add deve usar INGREDIENT como kind padrão quando não enviado")
    void add_shouldDefaultToIngredient_whenKindIsNull() {
        IncludeRequest request = IncludeRequest.builder()
                .name("açaí base").cost(new BigDecimal("2.50")).quantity(BigDecimal.ONE).build();

        IncludeResponse response = includeService.add(merchant.getId(), product.getId(), request);

        assertThat(response.getKind()).isEqualTo(IncludeKind.INGREDIENT);
    }

    @Test
    @DisplayName("add deve rejeitar produto de outro merchant")
    void add_shouldRejectProductFromAnotherMerchant() {
        Merchant other = createMerchant("outro");
        Category otherCat = categoryRepository.save(Category.builder()
                .merchant(other).name("Outro").build());
        Product otherProduct = productRepository.save(Product.builder()
                .merchant(other).name("Outro").price(new BigDecimal("10"))
                .status(ProductStatus.ACTIVE).category(otherCat).build());

        IncludeRequest request = IncludeRequest.builder()
                .name("x").cost(BigDecimal.ONE).quantity(BigDecimal.ONE).build();

        assertThatThrownBy(() -> includeService.add(merchant.getId(), otherProduct.getId(), request))
                .isInstanceOf(ProductNotFoundException.class);
    }
}
