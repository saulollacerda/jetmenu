package com.jetmenu.integration.anotaai;

import com.jetmenu.category.Category;
import com.jetmenu.category.CategoryRepository;
import com.jetmenu.integration.IntegrationTestBase;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.order.Order;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end coverage of the Anota.AI webhook against a real Postgres, from the raw body
 * exactly as captured in production.
 * <p>
 * The fixture is the delivery Anota.AI actually sends: the order at the <b>root</b> of the
 * body, not wrapped in the {@code {success, info}} envelope of {@code /ping/get/{id}}. The
 * existing {@code order_detail_*.json} fixtures carry that envelope and must not be used
 * here — binding to them is precisely the mistake that would import nothing while answering
 * 200.
 */
@DisplayName("Webhook da Anota.AI — integração com Postgres")
class AnotaAIWebhookIntegrationTest extends IntegrationTestBase {

    private static final String ORDER_ID = "68b8f0c4d1e2a30012ab34cd";
    private static final String ANOTA_MERCHANT_ID = "66c3ada81acfe90018b7ca85";
    private static final String PRODUCT_EXTERNAL_ID = "68a1b2c3d4e5f60012340001";
    private static final String SECRET = "segredo-de-teste-do-webhook";

    @Autowired private AnotaAIWebhookService webhookService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    private Merchant merchant;
    private byte[] body;

    @BeforeEach
    void setUpMerchantAndCatalog() {
        merchant = createMerchant();
        merchant.setAnotaAiApiKey("api-key-" + merchant.getId());
        merchant.setAnotaAiWebhookSecret(SECRET);
        merchant.setAnotaAiMerchantId(ANOTA_MERCHANT_ID);
        merchantRepository.save(merchant);

        Category category = categoryRepository.save(Category.builder()
                .merchant(merchant).name("Açaí").build());
        productRepository.save(Product.builder()
                .merchant(merchant)
                .name("Açaí 500ml")
                .externalId(PRODUCT_EXTERNAL_ID)
                .category(category)
                .price(new BigDecimal("27.99"))
                .status(ProductStatus.ACTIVE)
                .build());

        body = loadWebhookBody();
    }

    private static byte[] loadWebhookBody() {
        try (InputStream is = AnotaAIWebhookIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixtures/anotaai/webhook_order_realizado.json")) {
            if (is == null) {
                throw new IllegalStateException("fixture webhook_order_realizado.json não encontrada");
            }
            return is.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private AnotaAIWebhookService.Outcome deliver() {
        return webhookService.handle(merchant.getId().toString(), SECRET, body);
    }

    @Test
    @DisplayName("importa o pedido a partir do corpo na raiz")
    void shouldImportOrderFromRootBody() {
        AnotaAIWebhookService.Outcome outcome = deliver();

        assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.IMPORTED);

        Order order = orderRepository
                .findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId())
                .orElseThrow();
        assertThat(order.getOrigin()).isEqualTo(OrderOrigin.ANOTA_AI);
    }

    /**
     * The money invariant this integration has never had real data for: Anota.AI's
     * {@code total} already includes the delivery fee, so adding it again would inflate
     * revenue. Every other anotaai fixture has {@code deliveryFee = 0}, which is exactly why
     * the assertion in {@code AnotaAIOrderImportService} was never truly exercised here.
     */
    @Test
    @DisplayName("total já inclui a entrega — 55,98 em produtos + 4,00 de entrega = 59,98")
    void shouldNotDoubleCountDeliveryFee() {
        deliver();

        Order order = orderRepository
                .findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId())
                .orElseThrow();

        assertThat(order.getTotalValue()).isEqualByComparingTo(new BigDecimal("59.98"));
        assertThat(order.getDeliveryFee()).isEqualByComparingTo(new BigDecimal("4.00"));

        BigDecimal productsSubtotal = order.getTotalValue().subtract(order.getDeliveryFee());
        assertThat(productsSubtotal).isEqualByComparingTo(new BigDecimal("55.98"));
    }

    @Test
    @DisplayName("dois itens idênticos viram dois OrderItem — a receita de 55,98 depende disso")
    void shouldPersistTwoSeparateItems() {
        deliver();

        Order order = orderRepository
                .findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId())
                .orElseThrow();

        assertThat(order.getItems()).hasSize(2);
        assertThat(order.getItems())
                .allSatisfy(item -> assertThat(item.getUnitPrice())
                        .isEqualByComparingTo(new BigDecimal("27.99")));
    }

    @Test
    @DisplayName("reentrega da mesma entrega responde duplicata e não cria um segundo pedido")
    void shouldBeIdempotentAcrossRedeliveries() {
        assertThat(deliver()).isEqualTo(AnotaAIWebhookService.Outcome.IMPORTED);
        assertThat(deliver()).isEqualTo(AnotaAIWebhookService.Outcome.DUPLICATE);

        assertThat(orderRepository.findAll().stream()
                .filter(o -> ORDER_ID.equals(o.getExternalOrderId()))
                .toList()).hasSize(1);
    }

    @Test
    @DisplayName("segredo errado não importa nada")
    void shouldRejectWrongSecret() {
        assertThatThrownBy(() -> webhookService.handle(merchant.getId().toString(), "errado", body))
                .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

        assertThat(orderRepository.findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("merchant.id de outra loja é recusado — cadastro cruzado não contamina a contabilidade")
    void shouldRejectCrossMerchantDelivery() {
        merchant.setAnotaAiMerchantId("66000000000000000000ffff");
        merchantRepository.save(merchant);

        assertThatThrownBy(this::deliver)
                .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

        assertThat(orderRepository.findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("primeira entrega aprende o merchant.id da Anota.AI e persiste")
    void shouldLearnAnotaMerchantIdOnFirstDelivery() {
        merchant.setAnotaAiMerchantId(null);
        merchantRepository.save(merchant);

        deliver();

        Optional<Merchant> reloaded = merchantRepository
                .findByIdWithAnotaAiIntegration(merchant.getId());
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getAnotaAiMerchantId()).isEqualTo(ANOTA_MERCHANT_ID);
    }

    @Test
    @DisplayName("merchant desconhecido é recusado")
    void shouldRejectUnknownMerchant() {
        assertThatThrownBy(() -> webhookService.handle(UUID.randomUUID().toString(), SECRET, body))
                .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);
    }

    @Test
    @DisplayName("envelope {success, info} não importa nada — é 400, não 200 silencioso")
    void shouldRejectPingGetEnvelope() {
        String envelope = "{\"success\":true,\"info\":"
                + new String(body, StandardCharsets.UTF_8) + "}";

        assertThatThrownBy(() -> webhookService.handle(merchant.getId().toString(), SECRET,
                envelope.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);

        assertThat(orderRepository.findByExternalOrderIdAndMerchantId(ORDER_ID, merchant.getId()))
                .isEmpty();
    }
}
