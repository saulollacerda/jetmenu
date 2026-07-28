package com.jetmenu.integration.ifood.services;

import com.jetmenu.category.CatalogOrigin;
import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.integration.ifood.IfoodCatalogClient;
import com.jetmenu.integration.ifood.IfoodTokenService;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult.Outcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodCatalogImportService")
class IfoodCatalogImportServiceTest {

    @Mock private IfoodCatalogClient catalogClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private MerchantRepository merchantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    @InjectMocks
    private IfoodCatalogImportService service;

    private UUID merchantId;
    private Merchant merchant;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
        merchant.setIfoodMerchantId("ifood-m1");
        lenient().when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        lenient().when(tokenService.getAccessToken()).thenReturn("token-1");
        lenient().when(productRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(merchantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── DTO helpers ─────────────────────────────────────────────────────────────

    private static IfoodCatalogResponse catalog(String catalogId, String... contexts) {
        IfoodCatalogResponse catalog = new IfoodCatalogResponse();
        catalog.setCatalogId(catalogId);
        catalog.setContext(List.of(contexts));
        catalog.setStatus("AVAILABLE");
        return catalog;
    }

    private static IfoodCatalogCategoryResponse category(String id, String name,
                                                         IfoodCatalogCategoryResponse.Item... items) {
        IfoodCatalogCategoryResponse category = new IfoodCatalogCategoryResponse();
        category.setId(id);
        category.setName(name);
        category.setStatus("AVAILABLE");
        category.setTemplate("DEFAULT");
        category.setItems(List.of(items));
        return category;
    }

    private static IfoodCatalogCategoryResponse.Item item(String name, String externalCode, String price) {
        IfoodCatalogCategoryResponse.Item item = new IfoodCatalogCategoryResponse.Item();
        item.setId(UUID.randomUUID().toString());
        item.setName(name);
        item.setExternalCode(externalCode);
        item.setStatus("AVAILABLE");
        if (price != null) {
            IfoodCatalogCategoryResponse.Price p = new IfoodCatalogCategoryResponse.Price();
            p.setValue(new BigDecimal(price));
            item.setPrice(p);
        }
        return item;
    }

    private static IfoodCatalogCategoryResponse.ContextModifier modifier(String context,
                                                                         String price,
                                                                         String externalCode) {
        IfoodCatalogCategoryResponse.ContextModifier modifier =
                new IfoodCatalogCategoryResponse.ContextModifier();
        modifier.setCatalogContext(context);
        if (price != null) {
            IfoodCatalogCategoryResponse.Price p = new IfoodCatalogCategoryResponse.Price();
            p.setValue(new BigDecimal(price));
            modifier.setPrice(p);
        }
        modifier.setExternalCode(externalCode);
        return modifier;
    }

    private void givenRemoteCatalog(IfoodCatalogCategoryResponse... categories) {
        given(catalogClient.listCatalogs("token-1", "ifood-m1"))
                .willReturn(List.of(catalog("cat-default", "DEFAULT")));
        given(catalogClient.listCategories("token-1", "ifood-m1", "cat-default"))
                .willReturn(List.of(categories));
    }

    private void givenNoLocalMatches() {
        lenient().when(categoryRepository.findByExternalIdAndMerchantId(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(categoryRepository.findByNameAndMerchantId(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(productRepository.findByExternalIdAndMerchantId(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(productRepository.findByCanonicalNameAndMerchantId(anyString(), any()))
                .thenReturn(Optional.empty());
        lenient().when(productRepository.existsByNameAndMerchantId(anyString(), any()))
                .thenReturn(false);
    }

    private static HttpClientErrorException unauthorized() {
        return HttpClientErrorException.create(
                HttpStatus.UNAUTHORIZED, "Unauthorized", new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    // ── Tests ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("lança IllegalStateException quando o merchant não está conectado ao iFood")
    void shouldThrowWhenMerchantNotConnected() {
        merchant.setIfoodMerchantId(null);

        assertThatThrownBy(() -> service.importCatalog(merchantId))
                .isInstanceOf(IllegalStateException.class);
        then(catalogClient).should(never()).listCatalogs(anyString(), anyString());
    }

    @Test
    @DisplayName("importa item novo criando categoria e produto com externalId, canonicalName e preço")
    void shouldImportNewItemCreatingCategoryAndProduct() {
        givenRemoteCatalog(category("c1", "Lanches", item("X-Búrger", "BURGER_001", "25.00")));
        givenNoLocalMatches();

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        ArgumentCaptor<Category> categoryCaptor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(categoryCaptor.capture());
        assertThat(categoryCaptor.getValue().getName()).isEqualTo("Lanches");
        assertThat(categoryCaptor.getValue().getExternalId()).isEqualTo("c1");
        assertThat(categoryCaptor.getValue().getMerchant()).isEqualTo(merchant);
        assertThat(categoryCaptor.getValue().getOrigin()).isEqualTo(CatalogOrigin.IFOOD);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        Product saved = productCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("X-Búrger");
        assertThat(saved.getCanonicalName()).isEqualTo("x-burger");
        assertThat(saved.getExternalId()).isEqualTo("BURGER_001");
        assertThat(saved.getPrice()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.ACTIVE);
        assertThat(saved.getOrigin()).isEqualTo(CatalogOrigin.IFOOD);
        assertThat(saved.getCategory().getName()).isEqualTo("Lanches");
        assertThat(saved.getMerchant()).isEqualTo(merchant);

        assertThat(result.getImportedProducts()).isEqualTo(1);
        assertThat(result.getImportedCategories()).isEqualTo(1);
        assertThat(result.getItems()).singleElement().satisfies(outcome -> {
            assertThat(outcome.getOutcome()).isEqualTo(Outcome.IMPORTED);
            assertThat(outcome.getName()).isEqualTo("X-Búrger");
        });
    }

    @Test
    @DisplayName("categoria existente por externalId é reutilizada sem criar nem renomear")
    void shouldReuseCategoryMatchedByExternalId() {
        Category existing = Category.builder().id(UUID.randomUUID())
                .merchant(merchant).name("Meus Lanches").externalId("c1").build();
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();
        given(categoryRepository.findByExternalIdAndMerchantId("c1", merchantId))
                .willReturn(Optional.of(existing));

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        then(categoryRepository).should(never()).save(any());
        assertThat(result.getImportedCategories()).isZero();
        assertThat(result.getLinkedCategories()).isEqualTo(1);

        ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getCategory()).isEqualTo(existing);
    }

    @Test
    @DisplayName("categoria existente só por nome é vinculada preenchendo o externalId")
    void shouldLinkCategoryMatchedByNameFillingExternalId() {
        Category existing = Category.builder().id(UUID.randomUUID())
                .merchant(merchant).name("Lanches").build();
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();
        given(categoryRepository.findByNameAndMerchantId("Lanches", merchantId))
                .willReturn(Optional.of(existing));

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo("c1");
        assertThat(captor.getValue().getName()).isEqualTo("Lanches");
        assertThat(result.getLinkedCategories()).isEqualTo(1);
    }

    @Test
    @DisplayName("produto existente por externalId vira LINKED sem alterar nenhum campo")
    void shouldLinkProductMatchedByExternalIdWithoutTouchingFields() {
        Product existing = Product.builder().id(UUID.randomUUID()).merchant(merchant)
                .name("X-Burger da Casa").canonicalName("x-burger da casa")
                .externalId("BURGER_001").price(new BigDecimal("31.90"))
                .status(ProductStatus.ACTIVE).build();
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();
        given(productRepository.findByExternalIdAndMerchantId("BURGER_001", merchantId))
                .willReturn(Optional.of(existing));

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        then(productRepository).should(never()).save(any());
        assertThat(existing.getPrice()).isEqualByComparingTo(new BigDecimal("31.90"));
        assertThat(result.getLinkedProducts()).isEqualTo(1);
        assertThat(result.getImportedProducts()).isZero();
        assertThat(result.getItems().get(0).getOutcome()).isEqualTo(Outcome.LINKED);
    }

    @Test
    @DisplayName("produto existente por nome canônico sem externalId é vinculado preenchendo o código")
    void shouldLinkProductMatchedByCanonicalNameFillingExternalId() {
        Product existing = Product.builder().id(UUID.randomUUID()).merchant(merchant)
                .name("X-Burger").canonicalName("x-burger")
                .price(new BigDecimal("31.90")).status(ProductStatus.ACTIVE).build();
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();
        given(productRepository.findByCanonicalNameAndMerchantId("x-burger", merchantId))
                .willReturn(Optional.of(existing));

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getExternalId()).isEqualTo("BURGER_001");
        // preço local nunca é sobrescrito na re-importação
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("31.90"));
        assertThat(result.getLinkedProducts()).isEqualTo(1);
    }

    @Test
    @DisplayName("produto por nome canônico com externalId divergente vira SKIPPED (conflito)")
    void shouldSkipProductWithConflictingExternalId() {
        Product existing = Product.builder().id(UUID.randomUUID()).merchant(merchant)
                .name("X-Burger").canonicalName("x-burger")
                .externalId("OTHER_CODE").price(new BigDecimal("31.90"))
                .status(ProductStatus.ACTIVE).build();
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();
        given(productRepository.findByCanonicalNameAndMerchantId("x-burger", merchantId))
                .willReturn(Optional.of(existing));

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        then(productRepository).should(never()).save(any());
        assertThat(result.getSkippedProducts()).isEqualTo(1);
        assertThat(result.getItems().get(0).getOutcome()).isEqualTo(Outcome.SKIPPED);
        assertThat(result.getItems().get(0).getReason()).isNotBlank();
    }

    @Test
    @DisplayName("colisão de nome bruto sem match canônico vira SKIPPED, sem exceção")
    void shouldSkipOnRawNameCollisionWithoutException() {
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", null, "25.00")));
        givenNoLocalMatches();
        given(productRepository.existsByNameAndMerchantId("X-Burger", merchantId)).willReturn(true);

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        then(productRepository).should(never()).save(any());
        assertThat(result.getSkippedProducts()).isEqualTo(1);
        assertThat(result.getItems().get(0).getOutcome()).isEqualTo(Outcome.SKIPPED);
    }

    @Test
    @DisplayName("item sem preço (ex.: PIZZA precificada por opções) vira SKIPPED com motivo")
    void shouldSkipItemWithoutPrice() {
        givenRemoteCatalog(category("c1", "Pizzas", item("Pizza Calabresa", "PIZZA_01", null)));
        givenNoLocalMatches();

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        then(productRepository).should(never()).save(any());
        assertThat(result.getSkippedProducts()).isEqualTo(1);
        assertThat(result.getItems().get(0).getReason()).containsIgnoringCase("preço");
    }

    @Test
    @DisplayName("contextModifier do contexto DEFAULT sobrescreve preço e externalCode; outros são ignorados")
    void shouldApplyDefaultContextModifier() {
        IfoodCatalogCategoryResponse.Item remoteItem = item("X-Burger", "ROOT_CODE", "25.00");
        remoteItem.setContextModifiers(List.of(
                modifier("WHITELABEL", "99.00", "WL_CODE"),
                modifier("DEFAULT", "22.50", "DEFAULT_CODE")));
        givenRemoteCatalog(category("c1", "Lanches", remoteItem));
        givenNoLocalMatches();

        service.importCatalog(merchantId);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getPrice()).isEqualByComparingTo(new BigDecimal("22.50"));
        assertThat(captor.getValue().getExternalId()).isEqualTo("DEFAULT_CODE");
    }

    @Test
    @DisplayName("escolhe o catálogo com contexto DEFAULT entre vários; sem DEFAULT usa o primeiro")
    void shouldPickDefaultContextCatalog() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1")).willReturn(List.of(
                catalog("cat-indoor", "INDOOR"),
                catalog("cat-default", "DEFAULT")));
        given(catalogClient.listCategories("token-1", "ifood-m1", "cat-default"))
                .willReturn(List.of());

        service.importCatalog(merchantId);

        then(catalogClient).should().listCategories("token-1", "ifood-m1", "cat-default");
    }

    @Test
    @DisplayName("sem catálogo DEFAULT usa o primeiro disponível")
    void shouldFallBackToFirstCatalogWhenNoDefault() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1")).willReturn(List.of(
                catalog("cat-indoor", "INDOOR"),
                catalog("cat-wl", "WHITELABEL")));
        given(catalogClient.listCategories("token-1", "ifood-m1", "cat-indoor"))
                .willReturn(List.of());

        service.importCatalog(merchantId);

        then(catalogClient).should().listCategories("token-1", "ifood-m1", "cat-indoor");
    }

    @Test
    @DisplayName("marca ifoodCatalogImportedAt no merchant ao concluir")
    void shouldStampCatalogImportedAt() {
        givenRemoteCatalog(category("c1", "Lanches", item("X-Burger", "BURGER_001", "25.00")));
        givenNoLocalMatches();

        service.importCatalog(merchantId);

        ArgumentCaptor<Merchant> captor = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(captor.capture());
        assertThat(captor.getValue().getIfoodCatalogImportedAt()).isNotNull();
    }

    @Test
    @DisplayName("401 força refresh do token e repete a chamada uma única vez")
    void shouldRetryOnceOn401() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1")).willThrow(unauthorized());
        given(tokenService.handleUnauthorized()).willReturn("token-2");
        given(catalogClient.listCatalogs("token-2", "ifood-m1"))
                .willReturn(List.of(catalog("cat-default", "DEFAULT")));
        given(catalogClient.listCategories("token-2", "ifood-m1", "cat-default"))
                .willReturn(List.of());

        service.importCatalog(merchantId);

        then(tokenService).should().handleUnauthorized();
        then(catalogClient).should().listCategories("token-2", "ifood-m1", "cat-default");
    }

    @Test
    @DisplayName("sem catálogos disponíveis retorna resultado vazio e ainda marca a importação")
    void shouldReturnEmptyResultWhenNoCatalogs() {
        given(catalogClient.listCatalogs("token-1", "ifood-m1")).willReturn(List.of());

        IfoodCatalogImportResult result = service.importCatalog(merchantId);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getImportedProducts()).isZero();
        then(catalogClient).should(never()).listCategories(anyString(), anyString(), anyString());
    }
}
