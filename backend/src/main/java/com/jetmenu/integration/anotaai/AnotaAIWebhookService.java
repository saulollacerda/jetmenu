package com.jetmenu.integration.anotaai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetmenu.integration.anotaai.services.AnotaAICatalogSyncService;
import com.jetmenu.integration.anotaai.services.AnotaAIOrderImportService;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Importa o pedido que a Anota.AI entrega por webhook.
 *
 * <p><b>O path endereça, o header autentica.</b> O {@code merchantId} vem cru na URL — é um
 * UUID v4 (122 bits aleatórios), não enumerável, mas também não é segredo: vaza por design em
 * resposta de API, log e print, e não é rotacionável por ser a PK referenciada por FK de
 * pedidos, clientes, produtos e assinaturas. Toda a segurança do endpoint mora, portanto, no
 * segredo compartilhado — e a captura em produção confirmou que a Anota.AI <b>não assina</b>
 * as entregas, então não há segunda linha de defesa.
 *
 * <p><b>Três checagens, nesta ordem:</b> o segredo do header confere com o do lojista; o
 * corpo produz um pedido utilizável; e o {@code merchant.id} do corpo é o da loja vinculada.
 * A terceira existe porque as duas primeiras passariam se alguém colasse a URL e o token da
 * loja A no painel da loja B — os pedidos de B entrariam na contabilidade de A.
 *
 * <p><b>Tudo dentro do ciclo do request.</b> Nada de {@code @Async} aqui: no Cloud Run a CPU
 * é estrangulada fora do request, e trabalho assíncrono nesse caminho é trabalho que nunca
 * termina. Também não há chamada de rede no caminho feliz — o corpo já traz o pedido inteiro.
 *
 * <p><b>Nada de header ou corpo em log.</b> Em modo observação isso foi exceção deliberada;
 * com o import ligado seria vazamento contínuo de credencial e de dados pessoais de clientes
 * (nome, telefone e endereço vêm em toda entrega). O corpo bruto continua indo para
 * {@code external_order_raw_payload}, que tem retenção de 3 dias e leitura bem mais estreita
 * que a dos logs.
 */
@Service
public class AnotaAIWebhookService {

    private static final Logger log = LoggerFactory.getLogger(AnotaAIWebhookService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final AnotaAIOrderImportService orderImportService;
    private final ExternalOrderRawPayloadService rawPayloadService;
    private final AnotaAICatalogSyncService catalogSyncService;
    private final AnotaAIWebhookTokenService tokenService;
    private final boolean ifoodOrdersImportEnabled;

    public AnotaAIWebhookService(MerchantRepository merchantRepository,
                                 OrderRepository orderRepository,
                                 AnotaAIOrderImportService orderImportService,
                                 ExternalOrderRawPayloadService rawPayloadService,
                                 AnotaAICatalogSyncService catalogSyncService,
                                 AnotaAIWebhookTokenService tokenService,
                                 @Value("${anotaai.import-ifood-orders-enabled:false}")
                                 boolean ifoodOrdersImportEnabled) {
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.orderImportService = orderImportService;
        this.rawPayloadService = rawPayloadService;
        this.catalogSyncService = catalogSyncService;
        this.tokenService = tokenService;
        this.ifoodOrdersImportEnabled = ifoodOrdersImportEnabled;
    }

    /** O que aconteceu com a entrega. Tudo aqui vira 200 — a Anota.AI deve parar de reenviar. */
    public enum Outcome {
        IMPORTED,
        /** Pedido já conhecido: reentrega do webhook, ou o sync de reconciliação chegou antes. */
        DUPLICATE,
        /** Canal que não importamos (ex.: ifood com a flag desligada). */
        IGNORED
    }

    /**
     * Entrega que não reconhecemos: merchant inexistente, segredo errado ou loja da Anota.AI
     * diferente da vinculada. Vira <b>404</b> — confirmar que o merchant existe já entregaria
     * metade da informação a quem tem só a URL.
     */
    public static class UnknownDeliveryException extends RuntimeException {
        public UnknownDeliveryException(String message) {
            super(message);
        }
    }

    /** Corpo que não produz um pedido utilizável. Vira <b>400</b>: não é entrega legítima. */
    public static class InvalidPayloadException extends RuntimeException {
        public InvalidPayloadException(String message) {
            super(message);
        }
    }

    public Outcome handle(String merchantIdRaw, String authorizationHeader, byte[] rawBody) {
        String secret = authorizationHeader == null ? null : authorizationHeader.trim();
        // Sem credencial não há o que checar contra: recusa antes de tocar no banco, para que
        // uma varredura de URLs não vire carga de consulta.
        if (secret == null || secret.isEmpty()) {
            throw new UnknownDeliveryException("entrega sem credencial");
        }

        UUID merchantId = parseMerchantId(merchantIdRaw);
        Merchant merchant = merchantRepository.findByIdWithAnotaAiIntegration(merchantId)
                .orElseThrow(() -> new UnknownDeliveryException("entrega para merchant desconhecido"));

        if (!tokenService.matches(merchant.getAnotaAiWebhookSecret(), secret)) {
            // A mensagem nunca carrega o segredo — nem o esperado, nem o recebido.
            log.warn("[Anota.AI][webhook] credencial recusada — merchant={}", merchantId);
            throw new UnknownDeliveryException("credencial não confere");
        }

        String body = rawBody == null ? "" : new String(rawBody, StandardCharsets.UTF_8);
        AnotaAIOrderDetailResponse.OrderDetail detail = parseOrder(body);

        verifyOrLearnAnotaMerchantId(merchant, detail);

        OrderOrigin origin = AnotaAIOrderOrigins.resolve(detail.getSalesChannel(), ifoodOrdersImportEnabled);
        if (origin == null) {
            log.info("[Anota.AI][webhook] pedido={} ignorado — salesChannel='{}'",
                    detail.getId(), detail.getSalesChannel());
            return Outcome.IGNORED;
        }

        if (orderRepository.existsByExternalOrderIdAndMerchantId(detail.getId(), merchantId)) {
            log.info("[Anota.AI][webhook] pedido={} já importado — merchant={}", detail.getId(), merchantId);
            return Outcome.DUPLICATE;
        }

        try {
            orderImportService.importOrder(detail, merchantId, origin,
                    () -> syncCatalog(merchant, merchantId));
        } catch (DataIntegrityViolationException e) {
            // O existsBy acima é read-then-write: duas reentregas simultâneas leem "não
            // existe" antes de qualquer uma gravar. Quem desempata é o índice único
            // (uk_orders_merchant_external_order), e perder essa corrida é duplicata benigna.
            log.info("[Anota.AI][webhook] pedido={} já gravado em paralelo — merchant={}",
                    detail.getId(), merchantId);
            return Outcome.DUPLICATE;
        }

        rawPayloadService.save(merchantId, origin, detail.getId(), body);
        log.info("[Anota.AI][webhook] pedido={} importado — merchant={}", detail.getId(), merchantId);
        return Outcome.IMPORTED;
    }

    private UUID parseMerchantId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new UnknownDeliveryException("merchantId da URL não é um UUID");
        }
    }

    /**
     * O corpo do webhook traz o pedido <b>na raiz</b>, e não no envelope
     * {@code {success, info}} do {@code /ping/get/{id}} — vincular à classe externa
     * compilaria, rodaria e responderia 200 sem importar nada, porque
     * {@code @JsonIgnoreProperties(ignoreUnknown = true)} transforma o corpo em
     * {@code success=false, info=null} sem lançar exceção.
     * <p>
     * Pelo mesmo motivo, a ausência de exceção não é sinal de nada: um corpo que não bate
     * desserializa em objeto vazio. Daí a validação explícita — corpo que não produz um
     * pedido utilizável é 400, não 200, senão a falha silenciosa volta por outra porta.
     */
    private AnotaAIOrderDetailResponse.OrderDetail parseOrder(String body) {
        if (body.isBlank()) {
            throw new InvalidPayloadException("corpo vazio");
        }

        AnotaAIOrderDetailResponse.OrderDetail detail;
        try {
            detail = objectMapper.readValue(body, AnotaAIOrderDetailResponse.OrderDetail.class);
        } catch (Exception e) {
            throw new InvalidPayloadException("corpo não é um pedido em JSON");
        }

        if (detail == null || detail.getId() == null || detail.getId().isBlank()) {
            throw new InvalidPayloadException("corpo sem _id de pedido");
        }
        if (detail.getItems() == null || detail.getItems().isEmpty()) {
            throw new InvalidPayloadException("pedido sem itens");
        }
        return detail;
    }

    /**
     * Primeira entrega de um lojista preenche o vínculo; a partir daí, divergência recusa.
     * Mesmo padrão do {@code ifood_integration.ifood_merchant_id}.
     * <p>
     * Entrega sem {@code merchant.id} no corpo passa: não dá para verificar o que não veio, e
     * recusar por isso quebraria a integração se a Anota.AI mudar o formato. Fica o aviso no
     * log.
     */
    private void verifyOrLearnAnotaMerchantId(Merchant merchant,
                                              AnotaAIOrderDetailResponse.OrderDetail detail) {
        String fromBody = Optional.ofNullable(detail.getMerchant())
                .map(AnotaAIOrderDetailResponse.AnotaAIMerchant::getId)
                .filter(id -> !id.isBlank())
                .orElse(null);
        String linked = merchant.getAnotaAiMerchantId();

        if (fromBody == null) {
            log.warn("[Anota.AI][webhook] entrega sem merchant.id no corpo — merchant={}", merchant.getId());
            return;
        }

        if (linked == null || linked.isBlank()) {
            merchant.setAnotaAiMerchantId(fromBody);
            merchantRepository.save(merchant);
            log.info("[Anota.AI][webhook] loja da Anota.AI vinculada — merchant={} anotaMerchantId={}",
                    merchant.getId(), fromBody);
            return;
        }

        if (!linked.equals(fromBody)) {
            log.warn("[Anota.AI][webhook] entrega de outra loja da Anota.AI — merchant={} "
                            + "vinculada={} recebida={}", merchant.getId(), linked, fromBody);
            throw new UnknownDeliveryException("entrega de outra loja da Anota.AI");
        }
    }

    /**
     * Único caminho que sai para a rede, e só quando um produto do pedido não existe no
     * catálogo local. Falha aqui não derruba a entrega: o item sem produto é pulado pelo
     * import, como já acontece no polling.
     */
    private void syncCatalog(Merchant merchant, UUID merchantId) {
        String apiKey = merchant.getAnotaAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[Anota.AI][webhook] produto não encontrado e merchant={} sem API key — "
                    + "catálogo não sincronizado", merchantId);
            return;
        }
        try {
            catalogSyncService.sync(merchantId, apiKey, false);
        } catch (RuntimeException e) {
            log.error("[Anota.AI][webhook] falha ao sincronizar catálogo — merchant={}: {}",
                    merchantId, e.getMessage(), e);
        }
    }
}
