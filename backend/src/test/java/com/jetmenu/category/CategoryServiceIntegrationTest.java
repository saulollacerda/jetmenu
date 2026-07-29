package com.jetmenu.category;

import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CategoryService — integração com Postgres")
class CategoryServiceIntegrationTest extends IntegrationTestBase {

    @Autowired private CategoryService categoryService;
    @Autowired private CategoryRepository categoryRepository;

    private Merchant merchant;

    @BeforeEach
    void setup() {
        merchant = createMerchant();
    }

    @Test
    @DisplayName("create deve persistir categoria ligada ao merchant")
    void create_shouldPersistCategory() {
        CategoryResponse response = categoryService.create(merchant.getId(), CategoryRequest.builder().name("Açaí").build());

        Category persisted = categoryRepository.findById(response.getId()).orElseThrow();
        assertThat(persisted.getMerchant().getId()).isEqualTo(merchant.getId());
        assertThat(persisted.getName()).isEqualTo("Açaí");
    }

    @Test
    @DisplayName("update deve renomear")
    void update_shouldRename() {
        CategoryResponse created = categoryService.create(merchant.getId(), CategoryRequest.builder().name("Velho").build());

        categoryService.update(merchant.getId(), created.getId(), CategoryRequest.builder().name("Novo").build());

        Category persisted = categoryRepository.findById(created.getId()).orElseThrow();
        assertThat(persisted.getName()).isEqualTo("Novo");
    }

    @Test
    @DisplayName("delete deve remover categoria do banco")
    void delete_shouldRemove() {
        CategoryResponse created = categoryService.create(merchant.getId(), CategoryRequest.builder().name("X").build());

        categoryService.delete(merchant.getId(), created.getId());

        assertThat(categoryRepository.findById(created.getId())).isEmpty();
    }

    @Test
    @DisplayName("findAll deve paginar filtrando por nome")
    void findAll_shouldPaginate() {
        categoryService.create(merchant.getId(), CategoryRequest.builder().name("Açaí").build());
        categoryService.create(merchant.getId(), CategoryRequest.builder().name("Refrigerante").build());

        var page = categoryService.findAll(merchant.getId(), "Aç", PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Açaí");
    }

    @Test
    @DisplayName("findAll NÃO deve listar categorias de outros merchants")
    void findAll_shouldIsolate() {
        categoryService.create(merchant.getId(), CategoryRequest.builder().name("Minha").build());

        Merchant outro = createMerchant("Outro");
        categoryService.create(outro.getId(), CategoryRequest.builder().name("Do Outro").build());

        var page = categoryService.findAll(outro.getId(), null, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Do Outro");
    }
}
