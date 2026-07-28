package com.jetmenu.integration.ifood.services;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.integration.ifood.IfoodBadRequestException;
import com.jetmenu.integration.ifood.IfoodCatalogConflictException;
import com.jetmenu.integration.ifood.IfoodCatalogWriteClient;
import com.jetmenu.integration.ifood.IfoodResourceNotFoundException;
import com.jetmenu.integration.ifood.IfoodTokenService;
import com.jetmenu.integration.ifood.IfoodUnavailableException;
import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryCreatedResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogItemRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPriceUpdateRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult.Outcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusChange;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusUpdateRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IfoodCatalogPublishService")
class IfoodCatalogPublishServiceTest {

    private static final String IFOOD_MERCHANT_ID = "ifood-m1";

    @Mock private IfoodCatalogWriteClient writeClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private MerchantRepository merchantRepository;
    @Mock private ProductRepository productRepository;
    @Mock private CategoryRepository categoryRepository;

    private IfoodCatalogPublishService service;

    private UUID merchantId;
    private Merchant merchant;
    private Category category;

    @BeforeEach
    void setUp() {
        // backoff = 0 so the retry tests never actually sleep
        service = new IfoodCatalogPublishService(writeClient, tokenService, merchantRepository,
                productRepository, categoryRepository, 0L);

        merchantId = UUID.randomUUID();
        merchant = new Merchant();
        merchant.setId(merchantId);
        merchant.setIfoodMerchantId(IFOOD_MERCHANT_ID);

        category = Category.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .name("Lanches")
                .build();

        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(merchant));
        given(tokenService.getAccessToken()).willReturn("access.jwt");
        given(productRepository.save(any(Product.class))).willAnswer(i -> i.getArgument(0));
        given(categoryRepository.save(any(Category.class))).willAnswer(i -> i.getArgument(0));
    }

    private Product product(String name, String price, ProductStatus status) {
        return Product.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .name(name)
                .price(new BigDecimal(price))
                .status(status)
                .category(category)
                .build();
    }

    @Nested
    @DisplayName("publish")
    class Publish {

        @Test
        @DisplayName("cria a categoria no iFood, persiste o externalId e publica o item")
        void publish_shouldCreateCategoryAndPublishItem() {
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));
            given(writeClient.createCategory(eq("access.jwt"), eq(IFOOD_MERCHANT_ID), any()))
                    .willReturn(new IfoodCatalogCategoryCreatedResponse(
                            "cat-remote-1", "Lanches", "AVAILABLE", "DEFAULT"));

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of());

            assertThat(result.getPublishedProducts()).isEqualTo(1);
            assertThat(result.getSkippedProducts()).isZero();
            assertThat(result.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getProductId()).isEqualTo(burger.getId());
                assertThat(item.getName()).isEqualTo("X-Burger");
                assertThat(item.getExternalCode()).isEqualTo("JM-" + burger.getId());
                assertThat(item.getOutcome()).isEqualTo(Outcome.PUBLISHED);
                assertThat(item.getReason()).isNull();
            });

            ArgumentCaptor<IfoodCatalogCategoryRequest> categoryCaptor =
                    ArgumentCaptor.forClass(IfoodCatalogCategoryRequest.class);
            then(writeClient).should().createCategory(anyString(), anyString(), categoryCaptor.capture());
            assertThat(categoryCaptor.getValue().name()).isEqualTo("Lanches");
            assertThat(categoryCaptor.getValue().template()).isEqualTo("DEFAULT");
            assertThat(category.getExternalId()).isEqualTo("cat-remote-1");

            assertThat(burger.getIfoodItemId()).isNotNull();
            assertThat(burger.getIfoodProductId()).isNotNull();
            assertThat(burger.getIfoodPublishedAt()).isNotNull();
        }

        @Test
        @DisplayName("envia status/preço reais no contextModifier WHITELABEL e raiz UNAVAILABLE")
        void publish_shouldSendWhitelabelContextModifier() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));

            service.publish(merchantId, null);

            ArgumentCaptor<IfoodCatalogItemRequest> captor =
                    ArgumentCaptor.forClass(IfoodCatalogItemRequest.class);
            then(writeClient).should().upsertItem(eq("access.jwt"), eq(IFOOD_MERCHANT_ID), captor.capture());

            IfoodCatalogItemRequest sent = captor.getValue();
            assertThat(sent.item().type()).isEqualTo("DEFAULT");
            assertThat(sent.item().categoryId()).isEqualTo("cat-remote-1");
            assertThat(sent.item().status()).isEqualTo("UNAVAILABLE");
            assertThat(sent.item().price().value()).isEqualByComparingTo("25.00");
            assertThat(sent.item().contextModifiers()).singleElement().satisfies(cm -> {
                assertThat(cm.catalogContext()).isEqualTo("WHITELABEL");
                assertThat(cm.status()).isEqualTo("AVAILABLE");
                assertThat(cm.price().value()).isEqualByComparingTo("25.00");
                assertThat(cm.externalCode()).isEqualTo(sent.item().externalCode());
            });
            assertThat(sent.products()).singleElement().satisfies(p ->
                    assertThat(p.name()).isEqualTo("X-Burger"));
            assertThat(sent.optionGroups()).isEmpty();
            assertThat(sent.options()).isEmpty();
        }

        @Test
        @DisplayName("produto inativo vai para o WHITELABEL como UNAVAILABLE")
        void publish_shouldPublishInactiveProductAsUnavailable() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.INACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));

            service.publish(merchantId, List.of(burger.getId()));

            ArgumentCaptor<IfoodCatalogItemRequest> captor =
                    ArgumentCaptor.forClass(IfoodCatalogItemRequest.class);
            then(writeClient).should().upsertItem(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().item().contextModifiers().get(0).status())
                    .isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("sem lista de ids publica apenas os produtos ACTIVE")
        void publish_shouldPublishOnlyActiveProductsWhenNoIdsGiven() {
            category.setExternalId("cat-remote-1");
            Product active = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            Product inactive = product("Antigo", "10.00", ProductStatus.INACTIVE);
            given(productRepository.findAllByMerchantId(merchantId))
                    .willReturn(List.of(active, inactive));

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of());

            assertThat(result.getItems()).singleElement()
                    .satisfies(item -> assertThat(item.getName()).isEqualTo("X-Burger"));
            then(writeClient).should(times(1)).upsertItem(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("reaproveita os ids já publicados e não recria a categoria (idempotente)")
        void publish_shouldReuseExistingIdsAndCategory() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            burger.setIfoodItemId("item-existing");
            burger.setIfoodProductId("prod-existing");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));

            service.publish(merchantId, List.of(burger.getId()));

            then(writeClient).should(never()).createCategory(anyString(), anyString(), any());
            ArgumentCaptor<IfoodCatalogItemRequest> captor =
                    ArgumentCaptor.forClass(IfoodCatalogItemRequest.class);
            then(writeClient).should().upsertItem(anyString(), anyString(), captor.capture());
            assertThat(captor.getValue().item().id()).isEqualTo("item-existing");
            assertThat(captor.getValue().products().get(0).id()).isEqualTo("prod-existing");
        }

        @Test
        @DisplayName("usa o externalId do produto como externalCode sem sobrescrevê-lo")
        void publish_shouldUseExistingExternalIdAsExternalCode() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            burger.setExternalId("BURGER_001");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of(burger.getId()));

            assertThat(result.getItems().get(0).getExternalCode()).isEqualTo("BURGER_001");
            assertThat(burger.getExternalId()).isEqualTo("BURGER_001");
        }

        @Test
        @DisplayName("valida antes de enviar: nome longo, preço não positivo e produto sem categoria")
        void publish_shouldSkipInvalidProductsBeforeCallingIfood() {
            category.setExternalId("cat-remote-1");
            Product longName = product("N".repeat(101), "25.00", ProductStatus.ACTIVE);
            Product freeProduct = product("Brinde", "0.00", ProductStatus.ACTIVE);
            Product noCategory = product("Sem categoria", "10.00", ProductStatus.ACTIVE);
            noCategory.setCategory(null);
            Product valid = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId))
                    .willReturn(List.of(longName, freeProduct, noCategory, valid));

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of());

            assertThat(result.getPublishedProducts()).isEqualTo(1);
            assertThat(result.getSkippedProducts()).isEqualTo(3);
            assertThat(result.getItems()).filteredOn(i -> i.getOutcome() == Outcome.SKIPPED)
                    .allSatisfy(i -> assertThat(i.getReason()).isNotBlank());
            assertThat(result.getItems().get(0).getReason()).contains("100");
            assertThat(result.getItems().get(1).getReason()).contains("preço");
            assertThat(result.getItems().get(2).getReason()).contains("categoria");
            // only the valid product ever reaches iFood
            then(writeClient).should(times(1)).upsertItem(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("produto pedido que não existe no merchant vira FAILED, sem abortar o lote")
        void publish_shouldReportUnknownProductAsFailed() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            UUID unknownId = UUID.randomUUID();
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));

            IfoodCatalogPublishResult result =
                    service.publish(merchantId, List.of(burger.getId(), unknownId));

            assertThat(result.getPublishedProducts()).isEqualTo(1);
            assertThat(result.getItems()).filteredOn(i -> i.getOutcome() == Outcome.FAILED)
                    .singleElement()
                    .satisfies(i -> {
                        assertThat(i.getProductId()).isEqualTo(unknownId);
                        assertThat(i.getReason()).contains("não encontrado");
                    });
        }

        @Test
        @DisplayName("404 do iFood em um item vira FAILED e os demais seguem publicando")
        void publish_shouldReportNotFoundItemAsFailedWithoutAbortingBatch() {
            category.setExternalId("cat-remote-1");
            Product broken = product("Quebrado", "10.00", ProductStatus.ACTIVE);
            Product ok = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(broken, ok));
            willAnswer(invocation -> {
                IfoodCatalogItemRequest request = invocation.getArgument(2);
                if ("Quebrado".equals(request.products().get(0).name())) {
                    throw HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                            null, null, null);
                }
                return null;
            }).given(writeClient).upsertItem(anyString(), anyString(), any());

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of());

            assertThat(result.getPublishedProducts()).isEqualTo(1);
            assertThat(result.getItems().get(0).getOutcome()).isEqualTo(Outcome.FAILED);
            assertThat(result.getItems().get(0).getReason()).contains("não encontrado no iFood");
            assertThat(result.getItems().get(1).getOutcome()).isEqualTo(Outcome.PUBLISHED);
        }

        @Test
        @DisplayName("409 do iFood em um item vira FAILED com motivo de conflito")
        void publish_shouldReportConflictAsFailed() {
            category.setExternalId("cat-remote-1");
            Product burger = product("X-Burger", "25.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(burger));
            willThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null,
                    "{\"message\":\"externalCode duplicado\"}".getBytes(), null))
                    .given(writeClient).upsertItem(anyString(), anyString(), any());

            IfoodCatalogPublishResult result = service.publish(merchantId, List.of());

            assertThat(result.getItems()).singleElement().satisfies(item -> {
                assertThat(item.getOutcome()).isEqualTo(Outcome.FAILED);
                assertThat(item.getReason()).contains("Conflito");
            });
            // 4xx is never retried
            then(writeClient).should(times(1)).upsertItem(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("merchant sem conexão com o iFood falha antes de qualquer chamada")
        void publish_shouldFailWhenMerchantIsNotConnected() {
            merchant.setIfoodMerchantId(null);

            assertThatThrownBy(() -> service.publish(merchantId, List.of()))
                    .isInstanceOf(IllegalStateException.class);
            then(writeClient).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("syncPrices")
    class SyncPrices {

        @Test
        @DisplayName("envia todos os preços em uma única chamada e devolve o batchId")
        void syncPrices_shouldSendSingleBatchCall() {
            Product a = publishedProduct("X-Burger", "26.50");
            Product b = publishedProduct("Coca", "8.00");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(a, b));
            given(writeClient.updatePrices(anyString(), anyString(), any())).willReturn("batch-1");

            IfoodCatalogSyncResult result = service.syncPrices(merchantId, List.of());

            assertThat(result.getBatchId()).isEqualTo("batch-1");
            assertThat(result.getRequested()).isEqualTo(2);
            assertThat(result.getSkipped()).isEmpty();

            ArgumentCaptor<IfoodCatalogPriceUpdateRequest> captor =
                    ArgumentCaptor.forClass(IfoodCatalogPriceUpdateRequest.class);
            then(writeClient).should(times(1))
                    .updatePrices(eq("access.jwt"), eq(IFOOD_MERCHANT_ID), captor.capture());
            assertThat(captor.getValue().prices()).hasSize(2);
            assertThat(captor.getValue().prices().get(0).productId()).isEqualTo(a.getIfoodProductId());
            assertThat(captor.getValue().prices().get(0).price()).isEqualByComparingTo("26.50");
        }

        @Test
        @DisplayName("120 produtos viram uma única requisição (performance em massa)")
        void syncPrices_shouldBatchHundredsOfProductsInOneCall() {
            List<Product> products = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                products.add(publishedProduct("Produto " + i, "10.00"));
            }
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(products);
            given(writeClient.updatePrices(anyString(), anyString(), any())).willReturn("batch-1");

            IfoodCatalogSyncResult result = service.syncPrices(merchantId, List.of());

            assertThat(result.getRequested()).isEqualTo(120);
            then(writeClient).should(times(1)).updatePrices(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("produto ainda não publicado é reportado como skipped em pt-BR")
        void syncPrices_shouldSkipUnpublishedProducts() {
            Product published = publishedProduct("X-Burger", "26.50");
            Product neverPublished = product("Novo", "12.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId))
                    .willReturn(List.of(published, neverPublished));
            given(writeClient.updatePrices(anyString(), anyString(), any())).willReturn("batch-1");

            IfoodCatalogSyncResult result = service.syncPrices(merchantId, List.of());

            assertThat(result.getRequested()).isEqualTo(1);
            assertThat(result.getSkipped()).singleElement().satisfies(skipped -> {
                assertThat(skipped.getProductId()).isEqualTo(neverPublished.getId());
                assertThat(skipped.getReason()).contains("publicado");
            });
        }

        @Test
        @DisplayName("não chama o iFood quando todos os produtos foram ignorados")
        void syncPrices_shouldNotCallIfoodWhenNothingIsEligible() {
            Product neverPublished = product("Novo", "12.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(neverPublished));

            IfoodCatalogSyncResult result = service.syncPrices(merchantId, List.of());

            assertThat(result.getBatchId()).isNull();
            assertThat(result.getRequested()).isZero();
            then(writeClient).should(never()).updatePrices(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("preço inválido é ignorado antes de chegar ao iFood")
        void syncPrices_shouldSkipInvalidPrice() {
            Product invalid = publishedProduct("Brinde", "0.00");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(invalid));

            IfoodCatalogSyncResult result = service.syncPrices(merchantId, List.of());

            assertThat(result.getSkipped()).singleElement()
                    .satisfies(s -> assertThat(s.getReason()).contains("preço"));
            then(writeClient).should(never()).updatePrices(anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("syncStatus")
    class SyncStatus {

        @Test
        @DisplayName("envia as mudanças em um único lote e devolve o batchId")
        void syncStatus_shouldSendSingleBatchCall() {
            Product a = publishedProduct("X-Burger", "26.50");
            Product b = publishedProduct("Coca", "8.00");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(a, b));
            given(writeClient.updateStatus(anyString(), anyString(), any())).willReturn("batch-2");

            IfoodCatalogSyncResult result = service.syncStatus(merchantId, List.of(
                    new IfoodCatalogStatusChange(a.getId(), "UNAVAILABLE"),
                    new IfoodCatalogStatusChange(b.getId(), "AVAILABLE")));

            assertThat(result.getBatchId()).isEqualTo("batch-2");
            assertThat(result.getRequested()).isEqualTo(2);

            ArgumentCaptor<IfoodCatalogStatusUpdateRequest> captor =
                    ArgumentCaptor.forClass(IfoodCatalogStatusUpdateRequest.class);
            then(writeClient).should(times(1))
                    .updateStatus(eq("access.jwt"), eq(IFOOD_MERCHANT_ID), captor.capture());
            assertThat(captor.getValue().items()).hasSize(2);
            assertThat(captor.getValue().items().get(0).id()).isEqualTo(a.getIfoodItemId());
            assertThat(captor.getValue().items().get(0).status()).isEqualTo("UNAVAILABLE");
        }

        @Test
        @DisplayName("status fora de AVAILABLE/UNAVAILABLE é rejeitado antes do envio")
        void syncStatus_shouldSkipInvalidStatus() {
            Product a = publishedProduct("X-Burger", "26.50");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(a));

            IfoodCatalogSyncResult result = service.syncStatus(merchantId, List.of(
                    new IfoodCatalogStatusChange(a.getId(), "PAUSED")));

            assertThat(result.getBatchId()).isNull();
            assertThat(result.getSkipped()).singleElement()
                    .satisfies(s -> assertThat(s.getReason()).contains("Status inválido"));
            then(writeClient).should(never()).updateStatus(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("produto não publicado é reportado como skipped")
        void syncStatus_shouldSkipUnpublishedProduct() {
            Product neverPublished = product("Novo", "12.00", ProductStatus.ACTIVE);
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(neverPublished));

            IfoodCatalogSyncResult result = service.syncStatus(merchantId, List.of(
                    new IfoodCatalogStatusChange(neverPublished.getId(), "AVAILABLE")));

            assertThat(result.getSkipped()).singleElement()
                    .satisfies(s -> assertThat(s.getReason()).contains("publicado"));
        }

        @Test
        @DisplayName("409 do iFood no lote de status vira exceção tipada de conflito")
        void syncStatus_shouldTranslateConflict() {
            Product a = publishedProduct("X-Burger", "26.50");
            given(productRepository.findAllByMerchantId(merchantId)).willReturn(List.of(a));
            willThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", null,
                    "{\"message\":\"item bloqueado\"}".getBytes(), null))
                    .given(writeClient).updateStatus(anyString(), anyString(), any());

            assertThatThrownBy(() -> service.syncStatus(merchantId,
                    List.of(new IfoodCatalogStatusChange(a.getId(), "AVAILABLE"))))
                    .isInstanceOf(IfoodCatalogConflictException.class);
        }
    }

    @Nested
    @DisplayName("getBatch e resiliência")
    class BatchAndResilience {

        @Test
        @DisplayName("getBatch devolve o resultado consolidado do lote")
        void getBatch_shouldReturnBatchResult() {
            IfoodCatalogBatchResponse response = new IfoodCatalogBatchResponse(
                    "batch-1", "COMPLETED", 2, 0,
                    List.of(new IfoodCatalogBatchResponse.Result("prod-1", "SUCCESS")));
            given(writeClient.getBatch("access.jwt", IFOOD_MERCHANT_ID, "batch-1")).willReturn(response);

            assertThat(service.getBatch(merchantId, "batch-1")).isSameAs(response);
        }

        @Test
        @DisplayName("repete em 5xx com backoff e devolve o resultado da tentativa bem-sucedida")
        void getBatch_shouldRetryOnServerError() {
            IfoodCatalogBatchResponse response =
                    new IfoodCatalogBatchResponse("batch-1", "COMPLETED", 1, 0, List.of());
            given(writeClient.getBatch(anyString(), anyString(), anyString()))
                    .willThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                            "boom", null, null, null))
                    .willThrow(new ResourceAccessException("timeout"))
                    .willReturn(response);

            assertThat(service.getBatch(merchantId, "batch-1")).isSameAs(response);
            then(writeClient).should(times(3)).getBatch(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("5xx persistente esgota as tentativas e vira IfoodUnavailableException")
        void getBatch_shouldGiveUpAfterMaxAttempts() {
            given(writeClient.getBatch(anyString(), anyString(), anyString()))
                    .willThrow(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY,
                            "boom", null, null, null));

            assertThatThrownBy(() -> service.getBatch(merchantId, "batch-1"))
                    .isInstanceOf(IfoodUnavailableException.class);
            then(writeClient).should(times(3)).getBatch(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("nunca repete em 4xx — 404 é traduzido na primeira tentativa")
        void getBatch_shouldNotRetryOnClientError() {
            given(writeClient.getBatch(anyString(), anyString(), anyString()))
                    .willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found",
                            null, null, null));

            assertThatThrownBy(() -> service.getBatch(merchantId, "batch-1"))
                    .isInstanceOf(IfoodResourceNotFoundException.class);
            then(writeClient).should(times(1)).getBatch(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("400 do iFood vira IfoodBadRequestException com o detalhe do corpo")
        void getBatch_shouldTranslateBadRequest() {
            given(writeClient.getBatch(anyString(), anyString(), anyString()))
                    .willThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                            null, "{\"message\":\"batchId inválido\"}".getBytes(), null));

            assertThatThrownBy(() -> service.getBatch(merchantId, "batch-1"))
                    .isInstanceOf(IfoodBadRequestException.class)
                    .hasMessageContaining("batchId inválido");
            then(writeClient).should(times(1)).getBatch(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("401 dispara refresh do token e uma única repetição")
        void getBatch_shouldRefreshTokenOnUnauthorized() {
            IfoodCatalogBatchResponse response =
                    new IfoodCatalogBatchResponse("batch-1", "COMPLETED", 1, 0, List.of());
            given(tokenService.handleUnauthorized()).willReturn("refreshed.jwt");
            given(writeClient.getBatch("access.jwt", IFOOD_MERCHANT_ID, "batch-1"))
                    .willThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,
                            "Unauthorized", null, null, null));
            given(writeClient.getBatch("refreshed.jwt", IFOOD_MERCHANT_ID, "batch-1"))
                    .willReturn(response);

            assertThat(service.getBatch(merchantId, "batch-1")).isSameAs(response);
            then(tokenService).should(times(1)).handleUnauthorized();
        }
    }

    private Product publishedProduct(String name, String price) {
        Product product = product(name, price, ProductStatus.ACTIVE);
        product.setIfoodItemId("item-" + product.getId());
        product.setIfoodProductId("prod-" + product.getId());
        return product;
    }
}
