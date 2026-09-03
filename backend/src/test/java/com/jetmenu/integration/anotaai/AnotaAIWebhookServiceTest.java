package com.jetmenu.integration.anotaai;

import com.jetmenu.integration.anotaai.services.AnotaAIOrderImportService;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.integration.anotaai.services.AnotaAICatalogSyncService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * The webhook is the only credential check on a public endpoint: Anota.AI sends no
 * signature, so a mistake here is an open door into a merchant's books.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnotaAIWebhookService")
class AnotaAIWebhookServiceTest {

    private static final String SECRET = "segredo-do-lojista-abcdef";
    private static final String ANOTA_MERCHANT_ID = "66c3ada81acfe90018b7ca85";
    private static final String ORDER_ID = "68b8f0c4d1e2a30012ab34cd";

    @Mock private MerchantRepository merchantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private AnotaAIOrderImportService orderImportService;
    @Mock private ExternalOrderRawPayloadService rawPayloadService;
    @Mock private AnotaAICatalogSyncService catalogSyncService;

    private AnotaAIWebhookService service;

    private UUID merchantId;
    private Merchant merchant;
    private String body;

    @BeforeEach
    void setUp() {
        service = new AnotaAIWebhookService(merchantRepository, orderRepository, orderImportService,
                rawPayloadService, catalogSyncService, new AnotaAIWebhookTokenService(), false);
        merchantId = UUID.randomUUID();
        merchant = Merchant.builder().id(merchantId).build();
        merchant.setAnotaAiApiKey("api-key");
        merchant.setAnotaAiWebhookSecret(SECRET);
        merchant.setAnotaAiMerchantId(ANOTA_MERCHANT_ID);
        body = fixtureBody();
    }

    private static String fixtureBody() {
        try (var is = AnotaAIWebhookServiceTest.class.getClassLoader()
                .getResourceAsStream("fixtures/anotaai/webhook_order_realizado.json")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private AnotaAIWebhookService.Outcome handle(String merchantIdRaw, String secret, String payload) {
        return service.handle(merchantIdRaw, secret, payload == null ? null : payload.getBytes(StandardCharsets.UTF_8));
    }

    private void givenMerchantFound() {
        given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId)).willReturn(Optional.of(merchant));
    }

    // -------------------------------------------------------------------------
    // Credencial
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("credencial")
    class Credential {

        @Test
        @DisplayName("sem header Authorization recusa como entrega desconhecida e nem consulta o banco")
        void shouldRejectMissingSecret() {
            assertThatThrownBy(() -> handle(merchantId.toString(), null, body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

            then(merchantRepository).shouldHaveNoInteractions();
            then(orderImportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("header Authorization em branco é recusado")
        void shouldRejectBlankSecret() {
            assertThatThrownBy(() -> handle(merchantId.toString(), "   ", body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);
        }

        @Test
        @DisplayName("segredo errado é recusado e nada é importado")
        void shouldRejectWrongSecret() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), "segredo-errado", body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

            then(orderImportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("merchant sem segredo cadastrado recusa qualquer entrega")
        void shouldRejectWhenMerchantHasNoSecret() {
            merchant.setAnotaAiWebhookSecret(null);
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);
        }

        @Test
        @DisplayName("merchantId da URL que não é UUID é recusado sem consultar o banco")
        void shouldRejectNonUuidMerchantId() {
            assertThatThrownBy(() -> handle("nao-e-uuid", SECRET, body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

            then(merchantRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("merchant inexistente é recusado")
        void shouldRejectUnknownMerchant() {
            given(merchantRepository.findByIdWithAnotaAiIntegration(merchantId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);
        }

        @Test
        @DisplayName("o segredo nunca aparece na mensagem da exceção")
        void shouldNeverLeakSecretInException() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), "segredo-errado", body))
                    .hasMessageNotContaining(SECRET)
                    .hasMessageNotContaining("segredo-errado");
        }
    }

    // -------------------------------------------------------------------------
    // Vínculo com a loja da Anota.AI
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("vínculo com a loja da Anota.AI")
    class AnotaMerchantBinding {

        @Test
        @DisplayName("merchant.id divergente é recusado — cadastro cruzado entre lojas")
        void shouldRejectDivergentAnotaMerchantId() {
            merchant.setAnotaAiMerchantId("outro-id-da-anota");
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, body))
                    .isInstanceOf(AnotaAIWebhookService.UnknownDeliveryException.class);

            then(orderImportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("primeira entrega preenche o anota_ai_merchant_id ainda vazio")
        void shouldLearnAnotaMerchantIdOnFirstDelivery() {
            merchant.setAnotaAiMerchantId(null);
            givenMerchantFound();

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, body);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.IMPORTED);
            assertThat(merchant.getAnotaAiMerchantId()).isEqualTo(ANOTA_MERCHANT_ID);
            then(merchantRepository).should().save(merchant);
        }
    }

    // -------------------------------------------------------------------------
    // Corpo
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("corpo da entrega")
    class Payload {

        @Test
        @DisplayName("corpo vazio é 400 — não é entrega legítima")
        void shouldRejectEmptyBody() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, ""))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);
        }

        @Test
        @DisplayName("corpo que não é JSON é 400")
        void shouldRejectNonJsonBody() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, "isto nao e json"))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);
        }

        @Test
        @DisplayName("objeto vazio é 400, não 200 — Jackson aceita em silêncio o que não bate")
        void shouldRejectEmptyObject() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, "{}"))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);

            then(orderImportService).shouldHaveNoInteractions();
        }

        /**
         * The trap this whole integration nearly fell into: {@code /ping/get/{id}} answers
         * {@code {success, info}}, and binding the webhook to that envelope deserializes into
         * an empty object without throwing. It must not be mistaken for a valid delivery.
         */
        @Test
        @DisplayName("envelope {success, info} do /ping/get é 400 — o webhook manda o pedido na raiz")
        void shouldRejectPingGetEnvelope() {
            givenMerchantFound();
            String envelope = "{\"success\":true,\"info\":" + body + "}";

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET, envelope))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);

            then(orderImportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("pedido sem itens é 400")
        void shouldRejectOrderWithoutItems() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET,
                    "{\"_id\":\"abc\",\"items\":[]}"))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);
        }

        @Test
        @DisplayName("pedido sem _id é 400")
        void shouldRejectOrderWithoutId() {
            givenMerchantFound();

            assertThatThrownBy(() -> handle(merchantId.toString(), SECRET,
                    "{\"items\":[{\"name\":\"x\",\"quantity\":1}]}"))
                    .isInstanceOf(AnotaAIWebhookService.InvalidPayloadException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Importação
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("importação")
    class Import {

        @Test
        @DisplayName("entrega válida importa o pedido da raiz do corpo com origin ANOTA_AI")
        void shouldImportRootBody() {
            givenMerchantFound();

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, body);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.IMPORTED);

            ArgumentCaptor<AnotaAIOrderDetailResponse.OrderDetail> detail =
                    ArgumentCaptor.forClass(AnotaAIOrderDetailResponse.OrderDetail.class);
            then(orderImportService).should().importOrder(detail.capture(), eq(merchantId),
                    eq(OrderOrigin.ANOTA_AI), any());

            assertThat(detail.getValue().getId()).isEqualTo(ORDER_ID);
            assertThat(detail.getValue().getItems()).hasSize(2);
            assertThat(detail.getValue().getTotal()).isEqualTo(59.98);
            assertThat(detail.getValue().getDeliveryFee()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("guarda o corpo bruto para auditoria")
        void shouldStoreRawPayload() {
            givenMerchantFound();

            handle(merchantId.toString(), SECRET, body);

            then(rawPayloadService).should()
                    .save(eq(merchantId), eq(OrderOrigin.ANOTA_AI), eq(ORDER_ID), eq(body));
        }

        @Test
        @DisplayName("pedido já importado responde duplicata sem reimportar")
        void shouldSkipAlreadyImportedOrder() {
            givenMerchantFound();
            given(orderRepository.existsByExternalOrderIdAndMerchantId(ORDER_ID, merchantId))
                    .willReturn(true);

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, body);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.DUPLICATE);
            then(orderImportService).should(never()).importOrder(any(), any(), any(), any());
        }

        /**
         * The read-then-write above loses the race between two concurrent redeliveries; the
         * unique index is what actually settles it, and its violation is a benign duplicate.
         */
        @Test
        @DisplayName("violação do índice único vira duplicata benigna, não erro")
        void shouldTreatConstraintViolationAsDuplicate() {
            givenMerchantFound();
            willThrow(new DataIntegrityViolationException("uk_orders_merchant_external_order"))
                    .given(orderImportService).importOrder(any(), any(), any(), any());

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, body);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.DUPLICATE);
        }

        @Test
        @DisplayName("pedido de outro canal (ifood) é ignorado com a flag desligada")
        void shouldIgnoreIfoodChannelWhenFlagOff() {
            givenMerchantFound();
            String ifoodBody = body.replace("\"salesChannel\": \"anotaai\"", "\"salesChannel\": \"ifood\"");

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, ifoodBody);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.IGNORED);
            then(orderImportService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("com a flag ligada, pedido do ifood entra com origin IFOOD")
        void shouldImportIfoodChannelWhenFlagOn() {
            service = new AnotaAIWebhookService(merchantRepository, orderRepository, orderImportService,
                    rawPayloadService, catalogSyncService, new AnotaAIWebhookTokenService(), true);
            givenMerchantFound();
            String ifoodBody = body.replace("\"salesChannel\": \"anotaai\"", "\"salesChannel\": \"ifood\"");

            AnotaAIWebhookService.Outcome outcome = handle(merchantId.toString(), SECRET, ifoodBody);

            assertThat(outcome).isEqualTo(AnotaAIWebhookService.Outcome.IMPORTED);
            then(orderImportService).should()
                    .importOrder(any(), eq(merchantId), eq(OrderOrigin.IFOOD), any());
        }

        /**
         * Cloud Run only bills CPU during the request cycle, so the import must not fan out to
         * the network: the body already carries the whole order.
         */
        @Test
        @DisplayName("não chama o catálogo da Anota.AI quando todos os produtos são resolvidos")
        void shouldNotCallCatalogOnHappyPath() {
            givenMerchantFound();

            handle(merchantId.toString(), SECRET, body);

            then(catalogSyncService).shouldHaveNoInteractions();
        }
    }
}
