package com.jetmenu.product;

import com.jetmenu.category.CategoryRepository;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Mesmo tratamento dos backfills de billing: migração de dados de uma vez só que roda a cada
 * boot, e no Cloud Run "cada boot" é cada instância nova. Sem bean não há {@code run}.
 */
@DisplayName("Gate do LegacyProductCategoryBackfill")
class LegacyProductCategoryBackfillGateTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ProductRepository.class, () -> mock(ProductRepository.class))
            .withBean(CategoryRepository.class, () -> mock(CategoryRepository.class))
            .withBean(MerchantRepository.class, () -> mock(MerchantRepository.class))
            .withUserConfiguration(LegacyProductCategoryBackfill.class);

    @Test
    @DisplayName("sem a flag o bean existe")
    void shouldRegisterByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(LegacyProductCategoryBackfill.class));
    }

    @Test
    @DisplayName("com a flag desligada o bean não existe")
    void shouldNotRegisterWhenDisabled() {
        runner.withPropertyValues("app.startup.backfills-enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(LegacyProductCategoryBackfill.class));
    }
}
