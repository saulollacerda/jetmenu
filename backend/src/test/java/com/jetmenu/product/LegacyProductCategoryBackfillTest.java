package com.jetmenu.product;

import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
@DisplayName("LegacyProductCategoryBackfill")
class LegacyProductCategoryBackfillTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private MerchantRepository merchantRepository;


    @InjectMocks
    private LegacyProductCategoryBackfill backfill;

    @Test
    @DisplayName("não faz nada quando não há produtos sem categoria")
    void shouldDoNothingWhenNoOrphans() throws Exception {
        given(productRepository.findAllByCategoryIsNull()).willReturn(List.of());

        backfill.run();

        then(categoryRepository).should(never()).save(any());
        then(productRepository).should(never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("cria a categoria 'Sem categoria' por owner e atribui aos produtos órfãos")
    void shouldCreateDefaultCategoryAndAssignToOrphans() throws Exception {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();

        Product p1 = Product.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(ownerA).build()).name("Produto 1").build();
        Product p2 = Product.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(ownerA).build()).name("Produto 2").build();
        Product p3 = Product.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(ownerB).build()).name("Produto 3").build();

        given(productRepository.findAllByCategoryIsNull()).willReturn(List.of(p1, p2, p3));
        given(categoryRepository.findByNameAndMerchantId("Sem categoria", ownerA)).willReturn(Optional.empty());
        given(categoryRepository.findByNameAndMerchantId("Sem categoria", ownerB)).willReturn(Optional.empty());
        given(merchantRepository.getReferenceById(any(UUID.class)))
                .willAnswer(inv -> Merchant.builder().id(inv.getArgument(0)).build());

        Category createdA = Category.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(ownerA).build()).name("Sem categoria").build();
        Category createdB = Category.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(ownerB).build()).name("Sem categoria").build();
        given(categoryRepository.save(argThat(c -> c != null && ownerA.equals(c.getMerchant().getId()))))
                .willReturn(createdA);
        given(categoryRepository.save(argThat(c -> c != null && ownerB.equals(c.getMerchant().getId()))))
                .willReturn(createdB);

        backfill.run();

        then(categoryRepository).should(times(2)).save(any(Category.class));

        ArgumentCaptor<Iterable<Product>> captor = ArgumentCaptor.captor();
        then(productRepository).should(times(2)).saveAll(captor.capture());

        List<Iterable<Product>> savedBatches = captor.getAllValues();
        assertThat(savedBatches).hasSize(2);

        boolean hasOwnerABatch = savedBatches.stream().anyMatch(batch -> {
            List<Product> list = toList(batch);
            return list.size() == 2 && list.stream().allMatch(p -> p.getCategory() == createdA);
        });
        boolean hasOwnerBBatch = savedBatches.stream().anyMatch(batch -> {
            List<Product> list = toList(batch);
            return list.size() == 1 && list.get(0).getCategory() == createdB;
        });
        assertThat(hasOwnerABatch).as("owner A batch with 2 products tagged createdA").isTrue();
        assertThat(hasOwnerBBatch).as("owner B batch with 1 product tagged createdB").isTrue();
    }

    @Test
    @DisplayName("reutiliza categoria 'Sem categoria' existente em vez de duplicar (idempotência)")
    void shouldReuseExistingDefaultCategory() throws Exception {
        UUID merchantId = UUID.randomUUID();
        Product orphan = Product.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Produto").build();
        Category existing = Category.builder().id(UUID.randomUUID()).merchant(Merchant.builder().id(merchantId).build()).name("Sem categoria").build();

        given(productRepository.findAllByCategoryIsNull()).willReturn(List.of(orphan));
        given(categoryRepository.findByNameAndMerchantId("Sem categoria", merchantId)).willReturn(Optional.of(existing));

        backfill.run();

        then(categoryRepository).should(never()).save(any(Category.class));
        then(productRepository).should().saveAll(argThat(iter -> {
            List<Product> list = toList(iter);
            return list.size() == 1 && list.get(0).getCategory() == existing;
        }));
    }

    private static <T> List<T> toList(Iterable<T> iter) {
        java.util.ArrayList<T> list = new java.util.ArrayList<>();
        iter.forEach(list::add);
        return list;
    }
}
