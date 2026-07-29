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

    public AnotaAISyncService(AnotaAIClient anotaAIClient,
                               MerchantRepository merchantRepository,
                               CategoryRepository categoryRepository,
                               ProductRepository productRepository,
                               CustomerRepository customerRepository,
                               FeeRepository feeRepository,
                               OrderRepository orderRepository,
                               IngredientRepository ingredientRepository,
                               IncludeRepository includeRepository,
                               NotificationService notificationService,
                               OrderCostCalculatorService orderCostCalculatorService,
                               ExternalOrderRawPayloadService rawPayloadService,
                               com.jetmenu.order.OrderFichaService orderFichaService) {
        this.anotaAIClient = anotaAIClient;
        this.merchantRepository = merchantRepository;
        this.orderRepository = orderRepository;
        this.rawPayloadService = rawPayloadService;

        this.catalogSyncService = new AnotaAICatalogSyncService(
                anotaAIClient, merchantRepository, categoryRepository,
                productRepository, includeRepository);

        AnotaAICustomerResolver customerResolver = new AnotaAICustomerResolver(
                customerRepository, merchantRepository);
        AnotaAIProductResolver productResolver = new AnotaAIProductResolver(productRepository);
        AnotaAIExtraIngredientResolver extraIngredientResolver = new AnotaAIExtraIngredientResolver(
                ingredientRepository, notificationService);

        this.orderImportService = new AnotaAIOrderImportService(
                merchantRepository, orderRepository, feeRepository, includeRepository,
                orderCostCalculatorService, customerResolver, productResolver, extraIngredientResolver,
                orderFichaService);
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

            if (!"anotaai".equalsIgnoreCase(summary.getSalesChannel())) {
                log.info("[Anota.AI] pedido={} ignorado — salesChannel='{}'",
                        externalOrderId, summary.getSalesChannel());
                ordersSkipped++;
                continue;
            }

            log.info("[Anota.AI] pedido={} from='{}' salesChannel='{}'",
                    externalOrderId, summary.getFrom(), summary.getSalesChannel());

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
                        detailResponse.body().getInfo(), merchantId, OrderOrigin.ANOTA_AI, missingIngredientNames,
                        () -> catalogSyncService.sync(merchantId, apiKey, false),
                        catalogSynced);
                rawPayloadService.save(merchantId, OrderOrigin.ANOTA_AI,
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
