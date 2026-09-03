package com.jetmenu.integration.anotaai;

import com.jetmenu.category.CategoryRepository;
import com.jetmenu.customer.CustomerRepository;
import com.jetmenu.fee.FeeRepository;
import com.jetmenu.ingredient.IngredientRepository;
import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.anotaai.services.AnotaAICatalogSyncService;
import com.jetmenu.integration.anotaai.services.AnotaAICustomerResolver;
import com.jetmenu.integration.anotaai.services.AnotaAIExtraIngredientResolver;
import com.jetmenu.integration.anotaai.services.AnotaAIOrderImportService;
import com.jetmenu.integration.anotaai.services.AnotaAIProductResolver;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.notification.NotificationService;
import com.jetmenu.order.OrderOrigin;
import com.jetmenu.order.OrderRepository;
import com.jetmenu.product.IncludeRepository;
import com.jetmenu.product.OrderCostCalculatorService;
import com.jetmenu.product.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Facade do domínio de integração com a Anota.AI. Mantém a API pública estável
 * (catalog/orders sync) e delega para os serviços especializados em
 * {@code integration.anotaai.services}.
 */
@Service
public class AnotaAISyncService {

    private static final Logger log = LoggerFactory.getLogger(AnotaAISyncService.class);

    private final AnotaAIClient anotaAIClient;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final ExternalOrderRawPayloadService rawPayloadService;

    private final AnotaAICatalogSyncService catalogSyncService;
    private final AnotaAIOrderImportService orderImportService;

    /**
     * FEATURE FLAG TEMPORÁRIA — prévia dos pedidos do iFood.
     *
     * <p>Enquanto a integração direta com o iFood não está no ar, a Anota.AI já devolve em
     * {@code /ping/list} os pedidos que a loja recebeu pelo iFood ({@code salesChannel=ifood}).
     * Com a flag ligada, esses pedidos são importados e gravados com
     * {@link OrderOrigin#IFOOD} — o lojista vê o movimento do iFood dentro do JetMenu antes
     * da integração definitiva.
     *
     * <p>Desligada por padrão. Remover junto com o {@code salesChannel=ifood} daqui quando a
     * integração direta com o iFood assumir esses pedidos, para não importá-los em dobro.
     */
    private final boolean ifoodOrdersImportEnabled;

    public AnotaAISyncService(AnotaAIClient anotaAIClient,
                               MerchantRepository merchantRepository,
                               OrderRepository orderRepository,
                               ExternalOrderRawPayloadService rawPayloadService,
                               AnotaAICatalogSyncService catalogSyncService,
                               AnotaAIOrderImportService orderImportService,
                               @Value("${anotaai.import-ifood-orders-enabled:false}")
                               boolean ifoodOrdersImportEnabled) {
        this.anotaAIClient = anotaAIClient;
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.rawPayloadService = rawPayloadService;
        this.catalogSyncService = catalogSyncService;
        this.orderImportService = orderImportService;
        this.ifoodOrdersImportEnabled = ifoodOrdersImportEnabled;
    }

    @Transactional
    public AnotaAISyncResult syncCatalog(UUID merchantId) {
        return syncCatalog(merchantId, false);
    }

    @Transactional
    public AnotaAISyncResult syncCatalog(UUID merchantId, boolean clearRecipes) {
        String apiKey = resolveApiKey(merchantId);
        return catalogSyncService.sync(merchantId, apiKey, clearRecipes);
    }

    @Transactional
    public AnotaAISyncResult syncOrders(UUID merchantId) {
        log.info("[Anota.AI] sync de pedidos iniciado — merchant={}", merchantId);
        String apiKey = resolveApiKey(merchantId);
        AnotaAIOrderListResponse list = anotaAIClient.getOrderList(apiKey);

        int ordersImported = 0, ordersSkipped = 0;
        List<String> errors = new ArrayList<>();
        Set<String> missingIngredientNames = new LinkedHashSet<>();
        boolean[] catalogSynced = { false };

        if (list == null || list.getInfo() == null || list.getInfo().getDocs() == null) {
            return AnotaAISyncResult.builder().errors(errors).build();
        }

        log.info("[Anota.AI] /ping/list retornou {} pedidos", list.getInfo().getDocs().size());

        for (AnotaAIOrderListResponse.OrderSummary summary : list.getInfo().getDocs()) {
            String externalOrderId = summary.getId();

            OrderOrigin origin = resolveOrigin(summary.getSalesChannel());
            if (origin == null) {
                log.info("[Anota.AI] pedido={} ignorado — salesChannel='{}'",
                        externalOrderId, summary.getSalesChannel());
                ordersSkipped++;
                continue;
            }

            log.info("[Anota.AI] pedido={} from='{}' salesChannel='{}' origin={}",
                    externalOrderId, summary.getFrom(), summary.getSalesChannel(), origin);

            if (orderRepository.existsByExternalOrderIdAndMerchantId(externalOrderId, merchantId)) {
                ordersSkipped++;
                continue;
            }

            try {
                RawJsonResponse<AnotaAIOrderDetailResponse> detailResponse = anotaAIClient
                        .getOrderDetail(apiKey, externalOrderId);
                if (detailResponse == null || detailResponse.body() == null
                        || detailResponse.body().getInfo() == null) {
                    log.warn("[Anota.AI] pedido {} sem dados de detalhe", externalOrderId);
                    errors.add("Pedido " + externalOrderId + " sem dados de detalhe");
                    continue;
                }
                orderImportService.importOrder(
                        detailResponse.body().getInfo(), merchantId, origin, missingIngredientNames,
                        () -> catalogSyncService.sync(merchantId, apiKey, false),
                        catalogSynced);
                rawPayloadService.save(merchantId, origin,
                        externalOrderId, detailResponse.rawJson());
                ordersImported++;
                log.info("[Anota.AI] pedido {} importado", externalOrderId);
            } catch (RuntimeException e) {
                log.error("[Anota.AI] ERRO ao importar pedido {}: {}",
                        externalOrderId, e.getMessage(), e);
                errors.add("Pedido " + externalOrderId + ": " + e.getMessage());
            }
        }

        AnotaAISyncResult result = AnotaAISyncResult.builder()
                .ordersImported(ordersImported)
                .ordersSkipped(ordersSkipped)
                .missingIngredientNames(new ArrayList<>(missingIngredientNames))
                .errors(errors)
                .build();

        log.info("[Anota.AI] sync de pedidos concluído — merchant={} importados={} ignorados={} erros={}",
                merchantId, ordersImported, ordersSkipped, errors.size());

        return result;
    }

    private OrderOrigin resolveOrigin(String salesChannel) {
        return AnotaAIOrderOrigins.resolve(salesChannel, ifoodOrdersImportEnabled);
    }

    private String resolveApiKey(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new AnotaAIIntegrationException("Usuário não encontrado"));
        String apiKey = merchant.getAnotaAiApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new AnotaAIIntegrationException("API key do Anota.AI não configurada para este usuário");
        }
        return apiKey;
    }
}
