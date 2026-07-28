package com.jetmenu.integration.ifood.services;

import com.jetmenu.customer.Customer;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.ingredient.Ingredient;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.order.OrderStatus;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.argThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodOrderImportService")
class IfoodOrderImportServiceTest {

    @Mock private MerchantRepository merchantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private ProductRepository productRepository;
    @Mock private IngredientRepository ingredientRepository;
    @Mock private IncludeRepository includeRepository;
    @Mock private NotificationService notificationService;
    @Mock private OrderCostCalculatorService orderCostCalculatorService;
    @Mock private com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService rawPayloadService;
    @Mock private com.jetmenu.order.OrderFichaService orderFichaService;

    private IfoodOrderImportService importService;

    private static final String RAW_JSON = "{\"id\":\"ord-1\"}";

    private UUID merchantId;
    private Merchant merchant;
    private Product product;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
        merchant.setIfoodMerchantId("ifood-m1");

        product = Product.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .name("Açaí 500 ml")
                .canonicalName("acai 500 ml")
                .price(new BigDecimal("21.99"))
                .build();

        importService = new IfoodOrderImportService(
                merchantRepository, orderRepository, customerRepository, productRepository,
                ingredientRepository, includeRepository, notificationService, orderCostCalculatorService,
                rawPayloadService, orderFichaService);

        lenient().when(merchantRepository.findByIfoodMerchantId("ifood-m1")).thenReturn(Optional.of(merchant));
        lenient().when(merchantRepository.getReferenceById(merchantId)).thenReturn(merchant);
        lenient().when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(orderCostCalculatorService.computeOrderTotalCost(any(Order.class)))
                .thenReturn(BigDecimal.ZERO);
        lenient().when(includeRepository.findByProductIdAndProductMerchantId(any(), any()))
                .thenReturn(List.of());
    }

    private IfoodOrderDetailResponse baseDetail() {
        IfoodOrderDetailResponse detail = new IfoodOrderDetailResponse();
        detail.setId("ord-1");
        detail.setCategory("FOOD");
        detail.setTest(false);
        detail.setCreatedAt("2026-07-01T18:00:00Z");
        detail.setExtraInfo("Pago Online");

        IfoodOrderDetailResponse.MerchantInfo merchantInfo = new IfoodOrderDetailResponse.MerchantInfo();
        merchantInfo.setId("ifood-m1");
        merchantInfo.setName("Loja");
        detail.setMerchant(merchantInfo);

        IfoodOrderDetailResponse.CustomerInfo customer = new IfoodOrderDetailResponse.CustomerInfo();
        customer.setName("Maria Santos");
        IfoodOrderDetailResponse.Phone phone = new IfoodOrderDetailResponse.Phone();
        phone.setNumber("11999998888");
        customer.setPhone(phone);
        detail.setCustomer(customer);

        IfoodOrderDetailResponse.Item item = new IfoodOrderDetailResponse.Item();
        item.setExternalCode("PDV-1");
        item.setName("Açaí 500 ml");
        item.setQuantity(new BigDecimal("2"));
        item.setUnitPrice(new BigDecimal("21.99"));
        detail.setItems(List.of(item));

        IfoodOrderDetailResponse.Total total = new IfoodOrderDetailResponse.Total();
        total.setSubTotal(new BigDecimal("43.98"));
        total.setDeliveryFee(new BigDecimal("5.99"));
        total.setOrderAmount(new BigDecimal("49.97"));
        detail.setTotal(total);

        return detail;
    }

    @Nested
    @DisplayName("importOrder()")
    class ImportOrder {

        @Test
        @DisplayName("deve importar pedido FOOD concluído com origin=IFOOD, o status do evento e extraInfo")
        void shouldImportConcludedFoodOrder() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getOrigin() == OrderOrigin.IFOOD
                            && o.getStatus() == OrderStatus.PAID
                            && "ord-1".equals(o.getExternalOrderId())
                            && "Pago Online".equals(o.getExtraInfo())
                            && new BigDecimal("49.97").compareTo(o.getTotalValue()) == 0
                            && new BigDecimal("5.99").compareTo(o.getDeliveryFee()) == 0
                            && o.getFee() == null
                            && o.getItems().size() == 1
                            && new BigDecimal("21.99").compareTo(o.getItems().get(0).getUnitPrice()) == 0));
        }

        @Test
        @DisplayName("deve pular pedido com category diferente de FOOD")
        void shouldSkipNonFoodCategory() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setCategory("GROCERY");

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve importar pedido de teste (isTest=true) com status TEST, ignorando o status do evento")
        void shouldImportTestOrderWithTestStatus() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setTest(true);
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getStatus() == OrderStatus.TEST));
        }

        @Test
        @DisplayName("deve pular pedido de merchant desconhecido (ifoodMerchantId não autorizado)")
        void shouldSkipUnknownMerchant() {
            given(merchantRepository.findByIfoodMerchantId("ifood-m1")).willReturn(Optional.empty());

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve pular pedido duplicado (externalOrderId já importado)")
        void shouldSkipDuplicatedOrder() {
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(true);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve resolver produto por nome canônico quando externalCode não casa")
        void shouldResolveProductByCanonicalNameFallback() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getItems().get(0).setExternalCode(null);
            detail.getItems().get(0).setName("AÇAÍ  500 ML");

            given(productRepository.findByCanonicalNameAndMerchantId("acai 500 ml", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) -> o.getItems().size() == 1));
        }

        @Test
        @DisplayName("deve pular item sem produto correspondente (nem código nem nome) mas importar o pedido")
        void shouldSkipItemWithoutMatchingProduct() {
            IfoodOrderDetailResponse detail = baseDetail();
            IfoodOrderDetailResponse.Item unknown = new IfoodOrderDetailResponse.Item();
            unknown.setExternalCode("NOPE");
            unknown.setName("Produto Fantasma");
            unknown.setQuantity(BigDecimal.ONE);
            unknown.setUnitPrice(BigDecimal.TEN);
            detail.setItems(List.of(detail.getItems().get(0), unknown));

            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(productRepository.findByExternalIdAndMerchantId("NOPE", merchantId))
                    .willReturn(Optional.empty());
            given(productRepository.findByCanonicalNameAndMerchantId("produto fantasma", merchantId))
                    .willReturn(Optional.empty());
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) -> o.getItems().size() == 1));
        }

        @Test
        @DisplayName("deve notificar MISSING_PRODUCT quando item não casa com nenhum produto")
        void shouldNotifyMissingProductWhenItemHasNoMatch() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getItems().get(0).setExternalCode("NOPE");
            detail.getItems().get(0).setName("Produto Fantasma");

            given(productRepository.findByExternalIdAndMerchantId("NOPE", merchantId))
                    .willReturn(Optional.empty());
            given(productRepository.findByCanonicalNameAndMerchantId("produto fantasma", merchantId))
                    .willReturn(Optional.empty());
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            then(notificationService).should()
                    .createMissingProduct(eq("Produto Fantasma"), eq("produto fantasma"), eq(merchantId));
        }

        @Test
        @DisplayName("deve resolver complemento (option) por nome canônico e criar extra ingredient")
        void shouldResolveOptionAsExtraIngredient() {
            IfoodOrderDetailResponse detail = baseDetail();
            IfoodOrderDetailResponse.Option option = new IfoodOrderDetailResponse.Option();
            option.setName("Morango");
            option.setQuantity(BigDecimal.ONE);
            option.setPrice(new BigDecimal("1.50"));
            detail.getItems().get(0).setOptions(List.of(option));

            Ingredient morango = Ingredient.builder()
                    .id(UUID.randomUUID())
                    .name("Morango")
                    .canonicalName("morango")
                    .unit("g")
                    .costPerUnit(new BigDecimal("0.05"))
                    .defaultQuantity(new BigDecimal("30"))
                    .build();

            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("morango", merchantId))
                    .willReturn(Optional.of(morango));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getItems().get(0).getExtraIngredients().size() == 1
                            && "Morango".equals(o.getItems().get(0).getExtraIngredients().get(0).getIngredientName())
                            && new BigDecimal("30").compareTo(
                                    o.getItems().get(0).getExtraIngredients().get(0).getQuantity()) == 0));
        }

        @Test
        @DisplayName("deve notificar MISSING_INGREDIENT quando complemento não casa com ingrediente")
        void shouldNotifyMissingIngredientWhenOptionHasNoMatch() {
            IfoodOrderDetailResponse detail = baseDetail();
            IfoodOrderDetailResponse.Option option = new IfoodOrderDetailResponse.Option();
            option.setName("Pistache Raro");
            option.setQuantity(BigDecimal.ONE);
            detail.getItems().get(0).setOptions(List.of(option));

            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("pistache raro", merchantId))
                    .willReturn(Optional.empty());
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(notificationService).should()
                    .createMissingIngredient(eq("Pistache Raro"), eq("pistache raro"), eq(merchantId));
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getItems().get(0).getExtraIngredients().isEmpty()));
        }

        @Test
        @DisplayName("deve persistir a option não-casada como subItem não-casado no item do pedido")
        void shouldPersistUnmatchedOptionOnOrderItem() {
            IfoodOrderDetailResponse detail = baseDetail();
            IfoodOrderDetailResponse.Option option = new IfoodOrderDetailResponse.Option();
            option.setName("Pistache Raro");
            option.setQuantity(BigDecimal.valueOf(2));
            option.setUnitPrice(new BigDecimal("3.00"));
            option.setPrice(new BigDecimal("6.00"));
            detail.getItems().get(0).setOptions(List.of(option));

            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(ingredientRepository.findFirstByCanonicalNameAndMerchantIdOrderByIdAsc("pistache raro", merchantId))
                    .willReturn(Optional.empty());
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            then(orderRepository).should().save(argThat((Order o) -> {
                var unmatched = o.getItems().get(0).getUnmatchedSubItems();
                return unmatched.size() == 1
                        && "Pistache Raro".equals(unmatched.get(0).getRawName())
                        && "pistache raro".equals(unmatched.get(0).getCanonicalName())
                        && unmatched.get(0).getQuantity() == 2
                        && unmatched.get(0).getSalePriceTotal().compareTo(new BigDecimal("6.00")) == 0
                        && unmatched.get(0).getOrderItem() != null;
            }));
        }

        @Test
        @DisplayName("deve converter createdAt UTC para horário de Brasília")
        void shouldConvertCreatedAtToBrazilZone() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            // 2026-07-01T18:00:00Z == 15:00 em America/Sao_Paulo (UTC-3)
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getDateTime().getHour() == 15 && o.getDateTime().getDayOfMonth() == 1));
        }

        @Test
        @DisplayName("deve reutilizar cliente existente pelo telefone")
        void shouldReuseExistingCustomerByPhone() {
            Customer existing = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .name("Maria Santos")
                    .phone("11999998888")
                    .build();
            given(customerRepository.findByPhoneAndMerchantId("11999998888", merchantId))
                    .willReturn(Optional.of(existing));
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            then(customerRepository).should(never()).save(any(Customer.class));
            then(orderRepository).should().save(argThat((Order o) -> o.getCustomer() == existing));
        }

        @Test
        @DisplayName("deve reutilizar cliente existente pelo externalId do iFood antes do telefone")
        void shouldReuseExistingCustomerByExternalIdFirst() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getCustomer().setId("ifood-cust-1");

            Customer existing = Customer.builder()
                    .id(UUID.randomUUID())
                    .merchant(merchant)
                    .name("Maria Santos")
                    .externalId("ifood-cust-1")
                    .build();
            given(customerRepository.findByExternalIdAndMerchantId("ifood-cust-1", merchantId))
                    .willReturn(Optional.of(existing));
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            then(customerRepository).should(never()).findByPhoneAndMerchantId(anyString(), any());
            then(customerRepository).should(never()).save(any(Customer.class));
            then(orderRepository).should().save(argThat((Order o) -> o.getCustomer() == existing));
        }

        @Test
        @DisplayName("não deve deduplicar nem persistir telefone 0800 (proxy do iFood)")
        void shouldNotDedupOrPersistIfoodProxyPhone() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getCustomer().setId("ifood-cust-2");
            detail.getCustomer().getPhone().setNumber("0800 700 3020");

            given(customerRepository.findByExternalIdAndMerchantId("ifood-cust-2", merchantId))
                    .willReturn(Optional.empty());
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            then(customerRepository).should(never()).findByPhoneAndMerchantId(anyString(), any());
            then(customerRepository).should().save(argThat((Customer c) ->
                    c.getPhone() == null
                            && "ifood-cust-2".equals(c.getExternalId())
                            && "Maria Santos".equals(c.getName())));
        }

        @Test
        @DisplayName("deve importar com status PENDING quando o evento de origem é CONFIRMED")
        void shouldImportWithPendingStatus() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PENDING, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getStatus() == OrderStatus.PENDING));
        }

        @Test
        @DisplayName("deve importar com status CANCELLED quando o evento de origem é CANCELLED")
        void shouldImportWithCancelledStatus() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.CANCELLED, RAW_JSON);

            assertThat(imported).isTrue();
            then(orderRepository).should().save(argThat((Order o) ->
                    o.getStatus() == OrderStatus.CANCELLED));
        }

        @Test
        @DisplayName("deve salvar o payload bruto para auditoria após importar o pedido")
        void shouldSaveRawPayloadAfterImport() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isTrue();
            then(rawPayloadService).should()
                    .save(merchantId, OrderOrigin.IFOOD, "ord-1", RAW_JSON);
        }

        @Test
        @DisplayName("NÃO deve salvar payload bruto quando o pedido é pulado (duplicado)")
        void shouldNotSaveRawPayloadWhenOrderIsSkipped() {
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(true);

            boolean imported = importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            assertThat(imported).isFalse();
            then(rawPayloadService).should(never()).save(any(), any(), any(), any());
        }
    }

    private Order existingOrder(OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .externalOrderId("ord-1")
                .status(status)
                .build();
    }

    @Nested
    @DisplayName("concludeOrder()")
    class ConcludeOrder {

        @Test
        @DisplayName("deve atualizar pedido PENDING existente para PAID e retornar true")
        void shouldUpdateExistingPendingOrderToPaid() {
            Order order = existingOrder(OrderStatus.PENDING);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
            then(orderRepository).should().save(order);
        }

        @Test
        @DisplayName("não deve reverter pedido CANCELLED (CANCELLED sempre vence)")
        void shouldNotRevertCancelledOrder() {
            Order order = existingOrder(OrderStatus.CANCELLED);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("não deve promover pedido TEST para PAID (teste fica fora dos ganhos)")
        void shouldNotPromoteTestOrder() {
            Order order = existingOrder(OrderStatus.TEST);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.TEST);
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve ser idempotente para pedido já PAID (não salva de novo)")
        void shouldBeIdempotentForPaidOrder() {
            Order order = existingOrder(OrderStatus.PAID);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve retornar false quando o pedido não existe (aciona o import completo)")
        void shouldReturnFalseWhenOrderUnknown() {
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.empty());

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }

        @Test
        @DisplayName("deve retornar false quando o merchant iFood é desconhecido")
        void shouldReturnFalseWhenMerchantUnknown() {
            given(merchantRepository.findByIfoodMerchantId("ifood-m1")).willReturn(Optional.empty());

            boolean handled = importService.concludeOrder("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("cancelOrder()")
    class CancelOrder {

        @Test
        @DisplayName("deve cancelar pedido PENDING existente e notificar ORDER_CANCELLED")
        void shouldCancelExistingPendingOrderAndNotify() {
            Order order = existingOrder(OrderStatus.PENDING);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);
            then(notificationService).should().createOrderCancelled("ord-1", null, merchantId);
        }

        @Test
        @DisplayName("deve cancelar pedido já PAID (CANCELLED vence sobre PAID)")
        void shouldCancelPaidOrder() {
            Order order = existingOrder(OrderStatus.PAID);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
            then(orderRepository).should().save(order);
            then(notificationService).should().createOrderCancelled("ord-1", null, merchantId);
        }

        @Test
        @DisplayName("não deve cancelar nem notificar pedido TEST (status TEST é terminal)")
        void shouldNotCancelTestOrder() {
            Order order = existingOrder(OrderStatus.TEST);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.TEST);
            then(orderRepository).should(never()).save(any(Order.class));
            then(notificationService).should(never()).createOrderCancelled(anyString(), any(), any());
        }

        @Test
        @DisplayName("deve ser idempotente: pedido já CANCELLED não salva nem notifica de novo")
        void shouldBeIdempotentForCancelledOrder() {
            Order order = existingOrder(OrderStatus.CANCELLED);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            then(orderRepository).should(never()).save(any(Order.class));
            then(notificationService).should(never()).createOrderCancelled(anyString(), any(), any());
        }

        @Test
        @DisplayName("deve retornar false sem notificar quando o pedido não existe")
        void shouldReturnFalseWhenOrderUnknown() {
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.empty());

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(notificationService).should(never()).createOrderCancelled(anyString(), any(), any());
        }

        @Test
        @DisplayName("deve retornar false quando o merchant iFood é desconhecido")
        void shouldReturnFalseWhenMerchantUnknown() {
            given(merchantRepository.findByIfoodMerchantId("ifood-m1")).willReturn(Optional.empty());

            boolean handled = importService.cancelOrder("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("registerCancellationRequest()")
    class RegisterCancellationRequest {

        @Test
        @DisplayName("deve notificar o lojista SEM alterar o status do pedido")
        void shouldNotifyWithoutTouchingOrderStatus() {
            Order order = existingOrder(OrderStatus.PENDING);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.registerCancellationRequest("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
            then(orderRepository).should(never()).save(any(Order.class));
            then(notificationService).should()
                    .createOrderCancellationRequested(order.getId(), "ord-1", merchantId);
        }

        @Test
        @DisplayName("não deve notificar pedido já CANCELLED (nada a responder)")
        void shouldNotNotifyCancelledOrder() {
            Order order = existingOrder(OrderStatus.CANCELLED);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.registerCancellationRequest("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            then(notificationService).should(never())
                    .createOrderCancellationRequested(any(), anyString(), any());
        }

        @Test
        @DisplayName("não deve notificar pedido de teste")
        void shouldNotNotifyTestOrder() {
            Order order = existingOrder(OrderStatus.TEST);
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.of(order));

            boolean handled = importService.registerCancellationRequest("ord-1", "ifood-m1");

            assertThat(handled).isTrue();
            then(notificationService).should(never())
                    .createOrderCancellationRequested(any(), anyString(), any());
        }

        @Test
        @DisplayName("deve retornar false quando o pedido ainda não foi importado")
        void shouldReturnFalseWhenOrderUnknown() {
            given(orderRepository.findByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(Optional.empty());

            boolean handled = importService.registerCancellationRequest("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(notificationService).should(never())
                    .createOrderCancellationRequested(any(), anyString(), any());
        }

        @Test
        @DisplayName("deve retornar false quando o merchant iFood é desconhecido")
        void shouldReturnFalseWhenMerchantUnknown() {
            given(merchantRepository.findByIfoodMerchantId("ifood-m1")).willReturn(Optional.empty());

            boolean handled = importService.registerCancellationRequest("ord-1", "ifood-m1");

            assertThat(handled).isFalse();
            then(orderRepository).should(never()).save(any(Order.class));
        }
    }

    @Nested
    @DisplayName("ficha do pedido")
    class OrderFicha {

        @Test
        @DisplayName("pedido importado recebe o snapshot da ficha do pedido uma única vez")
        void importedOrderGetsOrderFichaSnapshotOnce() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);
            given(orderFichaService.buildSnapshot(merchantId)).willReturn(new java.util.ArrayList<>(List.of(
                    com.jetmenu.order.OrderFichaIngredient.builder()
                            .quantity(BigDecimal.ONE).costPerUnit(new BigDecimal("0.50"))
                            .ingredientName("Sacola").ingredientUnit("un").build())));

            importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            Order saved = captor.getValue();
            // item.quantity = 2, mas a ficha do pedido entra uma vez só
            assertThat(saved.getOrderFicha()).hasSize(1);
            assertThat(saved.getOrderFicha().get(0).getIngredientName()).isEqualTo("Sacola");
            assertThat(saved.getOrderFicha().get(0).getOrder()).isSameAs(saved);
        }

        @Test
        @DisplayName("lojista sem ficha do pedido: pedido importado sem linhas — no-op")
        void importedOrderWithoutOrderFichaIsNoOp() {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);
            given(orderFichaService.buildSnapshot(merchantId)).willReturn(new java.util.ArrayList<>());

            importService.importOrder(baseDetail(), OrderStatus.PAID, RAW_JSON);

            org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            assertThat(captor.getValue().getOrderFicha()).isEmpty();
        }
    }

    /**
     * Fields required by the iFood Order module homologation: card brand, cash change,
     * coupon value and who sponsors it, observations, pickup code and customer document.
     */
    @Nested
    @DisplayName("detalhes de homologação (pagamento, cupom, observações)")
    class HomologationDetails {

        private Order importAndCapture(IfoodOrderDetailResponse detail) {
            given(productRepository.findByExternalIdAndMerchantId("PDV-1", merchantId))
                    .willReturn(Optional.of(product));
            given(orderRepository.existsByExternalOrderIdAndMerchantId("ord-1", merchantId))
                    .willReturn(false);

            importService.importOrder(detail, OrderStatus.PAID, RAW_JSON);

            org.mockito.ArgumentCaptor<Order> captor = org.mockito.ArgumentCaptor.forClass(Order.class);
            then(orderRepository).should().save(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("deve persistir displayId, tipo e timing do pedido")
        void shouldPersistOrderIdentity() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setDisplayId("3421");
            detail.setOrderType("TAKEOUT");
            detail.setOrderTiming("SCHEDULED");

            Order saved = importAndCapture(detail);

            assertThat(saved.getDisplayId()).isEqualTo("3421");
            assertThat(saved.getOrderType()).isEqualTo(com.jetmenu.order.OrderType.TAKEOUT);
            assertThat(saved.getOrderTiming()).isEqualTo(com.jetmenu.order.OrderTiming.SCHEDULED);
        }

        @Test
        @DisplayName("deve ignorar orderType/orderTiming desconhecidos em vez de quebrar a importação")
        void shouldIgnoreUnknownOrderTypeAndTiming() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setOrderType("SOMETHING_NEW");
            detail.setOrderTiming("");

            Order saved = importAndCapture(detail);

            assertThat(saved.getOrderType()).isNull();
            assertThat(saved.getOrderTiming()).isNull();
        }

        @Test
        @DisplayName("deve persistir o CPF/CNPJ do cliente informado para a nota fiscal")
        void shouldPersistCustomerDocument() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getCustomer().setDocumentNumber("12345678909");

            Order saved = importAndCapture(detail);

            assertThat(saved.getCustomerDocument()).isEqualTo("12345678909");
        }

        @Test
        @DisplayName("deve persistir cada meio de pagamento com bandeira do cartão e troco")
        void shouldPersistPaymentMethods() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setPayments(payments());

            Order saved = importAndCapture(detail);

            assertThat(saved.getPaymentPrepaidAmount()).isEqualByComparingTo("10.00");
            assertThat(saved.getPaymentPendingAmount()).isEqualByComparingTo("39.97");
            assertThat(saved.getPaymentMethods()).hasSize(2);

            com.jetmenu.order.OrderPaymentMethod cash = saved.getPaymentMethods().get(0);
            assertThat(cash.getOrder()).isSameAs(saved);
            assertThat(cash.getMethod()).isEqualTo("CASH");
            assertThat(cash.getType()).isEqualTo("OFFLINE");
            assertThat(cash.getCurrency()).isEqualTo("BRL");
            assertThat(cash.getValue()).isEqualByComparingTo("39.97");
            assertThat(cash.getChangeFor()).isEqualByComparingTo("50.00");
            assertThat(cash.getCardBrand()).isNull();

            com.jetmenu.order.OrderPaymentMethod credit = saved.getPaymentMethods().get(1);
            assertThat(credit.getCardBrand()).isEqualTo("VISA");
            assertThat(credit.getChangeFor()).isNull();
        }

        @Test
        @DisplayName("deve somar o cupom e o rateio entre iFood e Loja")
        void shouldPersistBenefitSplit() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setBenefits(List.of(
                    benefit("12.00", "CART", sponsor("IFOOD", "8.00"), sponsor("MERCHANT", "4.00")),
                    benefit("3.00", "DELIVERY_FEE", sponsor("MERCHANT", "3.00"))));

            Order saved = importAndCapture(detail);

            assertThat(saved.getDiscountTotal()).isEqualByComparingTo("15.00");
            assertThat(saved.getDiscountIfoodValue()).isEqualByComparingTo("8.00");
            assertThat(saved.getDiscountMerchantValue()).isEqualByComparingTo("7.00");
        }

        @Test
        @DisplayName("não deve alterar totalValue/deliveryFee por causa do cupom")
        void shouldNotChangeMoneyFieldsBecauseOfBenefits() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setBenefits(List.of(benefit("12.00", "CART", sponsor("IFOOD", "12.00"))));

            Order saved = importAndCapture(detail);

            assertThat(saved.getTotalValue()).isEqualByComparingTo("49.97");
            assertThat(saved.getDeliveryFee()).isEqualByComparingTo("5.99");
        }

        @Test
        @DisplayName("deve persistir observações de entrega e o código de coleta")
        void shouldPersistDeliveryDetails() {
            IfoodOrderDetailResponse detail = baseDetail();
            IfoodOrderDetailResponse.Delivery delivery = new IfoodOrderDetailResponse.Delivery();
            delivery.setMode("DEFAULT");
            delivery.setDeliveredBy("MERCHANT");
            delivery.setDeliveryDateTime("2026-07-01T21:40:00Z");
            delivery.setObservations("Portão azul, tocar a campainha");
            delivery.setPickupCode("9182");
            detail.setDelivery(delivery);

            Order saved = importAndCapture(detail);

            assertThat(saved.getDeliveryMode()).isEqualTo("DEFAULT");
            assertThat(saved.getDeliveredBy()).isEqualTo("MERCHANT");
            // 21:40Z → 18:40 em America/Sao_Paulo
            assertThat(saved.getDeliveryDateTime())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 7, 1, 18, 40));
            assertThat(saved.getDeliveryObservations()).isEqualTo("Portão azul, tocar a campainha");
            assertThat(saved.getPickupCode()).isEqualTo("9182");
        }

        @Test
        @DisplayName("deve persistir os dados de retirada (takeout)")
        void shouldPersistTakeoutDetails() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.setOrderType("TAKEOUT");
            IfoodOrderDetailResponse.Takeout takeout = new IfoodOrderDetailResponse.Takeout();
            takeout.setMode("DEFAULT");
            takeout.setTakeoutDateTime("2026-07-01T21:30:00Z");
            detail.setTakeout(takeout);

            Order saved = importAndCapture(detail);

            assertThat(saved.getTakeoutMode()).isEqualTo("DEFAULT");
            assertThat(saved.getTakeoutDateTime())
                    .isEqualTo(java.time.LocalDateTime.of(2026, 7, 1, 18, 30));
        }

        @Test
        @DisplayName("deve persistir a observação do item")
        void shouldPersistItemObservations() {
            IfoodOrderDetailResponse detail = baseDetail();
            detail.getItems().get(0).setObservations("sem granola");

            Order saved = importAndCapture(detail);

            assertThat(saved.getItems()).hasSize(1);
            assertThat(saved.getItems().get(0).getObservations()).isEqualTo("sem granola");
        }

        @Test
        @DisplayName("payload sem os blocos novos importa com os campos nulos")
        void shouldImportWithoutTheNewBlocks() {
            Order saved = importAndCapture(baseDetail());

            assertThat(saved.getPaymentMethods()).isEmpty();
            assertThat(saved.getPaymentPrepaidAmount()).isNull();
            assertThat(saved.getPaymentPendingAmount()).isNull();
            assertThat(saved.getDiscountTotal()).isNull();
            assertThat(saved.getDiscountIfoodValue()).isNull();
            assertThat(saved.getDiscountMerchantValue()).isNull();
            assertThat(saved.getDeliveryMode()).isNull();
            assertThat(saved.getPickupCode()).isNull();
            assertThat(saved.getTakeoutMode()).isNull();
            assertThat(saved.getCustomerDocument()).isNull();
            assertThat(saved.getItems().get(0).getObservations()).isNull();
        }

        private IfoodOrderDetailResponse.Payments payments() {
            IfoodOrderDetailResponse.Payments payments = new IfoodOrderDetailResponse.Payments();
            payments.setPrepaid(new BigDecimal("10.00"));
            payments.setPending(new BigDecimal("39.97"));

            IfoodOrderDetailResponse.PaymentMethod cash = new IfoodOrderDetailResponse.PaymentMethod();
            cash.setMethod("CASH");
            cash.setType("OFFLINE");
            cash.setCurrency("BRL");
            cash.setValue(new BigDecimal("39.97"));
            IfoodOrderDetailResponse.Cash cashInfo = new IfoodOrderDetailResponse.Cash();
            cashInfo.setChangeFor(new BigDecimal("50.00"));
            cash.setCash(cashInfo);

            IfoodOrderDetailResponse.PaymentMethod credit = new IfoodOrderDetailResponse.PaymentMethod();
            credit.setMethod("CREDIT");
            credit.setType("ONLINE");
            credit.setCurrency("BRL");
            credit.setValue(new BigDecimal("10.00"));
            IfoodOrderDetailResponse.Card card = new IfoodOrderDetailResponse.Card();
            card.setBrand("VISA");
            credit.setCard(card);

            payments.setMethods(List.of(cash, credit));
            return payments;
        }

        private IfoodOrderDetailResponse.Benefit benefit(
                String value, String target, IfoodOrderDetailResponse.SponsorshipValue... sponsors) {
            IfoodOrderDetailResponse.Benefit benefit = new IfoodOrderDetailResponse.Benefit();
            benefit.setValue(new BigDecimal(value));
            benefit.setTarget(target);
            benefit.setSponsorshipValues(List.of(sponsors));
            return benefit;
        }

        private IfoodOrderDetailResponse.SponsorshipValue sponsor(String name, String value) {
            IfoodOrderDetailResponse.SponsorshipValue sponsor = new IfoodOrderDetailResponse.SponsorshipValue();
            sponsor.setName(name);
            sponsor.setValue(new BigDecimal(value));
            return sponsor;
        }
    }
}
