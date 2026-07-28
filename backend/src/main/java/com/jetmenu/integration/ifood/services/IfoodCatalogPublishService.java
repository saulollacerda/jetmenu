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
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult.ItemOutcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult.Outcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusChange;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusUpdateRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult.SkippedProduct;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.jetmenu.product.Product;
import com.jetmenu.product.ProductRepository;
import com.jetmenu.product.ProductStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Publica o catálogo do JetMenu no iFood — o caminho inverso de
 * {@link IfoodCatalogImportService}. Escopo confirmado em {@code docs/integrations/ifood/CATALOG.md}:
 *
 * <ul>
 *   <li><b>Somente WHITELABEL</b> (Cardápio Digital): a raiz do item sempre vai como
 *       {@code UNAVAILABLE} e o status/preço/externalCode reais viajam num
 *       {@code contextModifiers} com {@code catalogContext: WHITELABEL}. Contextos não
 *       listados herdam a raiz, então o item nunca aparece em Entrega ({@code DEFAULT}).</li>
 *   <li><b>Um único catálogo</b>, sem multi-catálogo.</li>
 *   <li>Complementos, PIZZA, COMBO_V2, agendamento e estoque ficam fora do escopo.</li>
 * </ul>
 *
 * <p>Resiliência exigida pela homologação do iFood:
 * <ul>
 *   <li>Validação <b>antes</b> do envio — nenhum payload inválido chega à API; o produto
 *       reprovado vira {@code SKIPPED} com motivo em pt-BR e o lote continua.</li>
 *   <li>Backoff exponencial só para falhas transitórias ({@code 5xx} e rede), até
 *       {@value #MAX_ATTEMPTS} tentativas; {@code 4xx} nunca é repetido.</li>
 *   <li>{@code 401} força um refresh de token e uma única repetição, fora da contagem.</li>
 *   <li>Erros do iFood viram exceções tipadas — nada falha em silêncio.</li>
 * </ul>
 *
 * <p>Não é {@code @Transactional} de propósito: cada id gerado é gravado assim que o item é
 * aceito pelo iFood, para que uma falha no meio do lote não perca a identidade já criada lá
 * (o que faria a republicação duplicar itens).
 */
@Service
public class IfoodCatalogPublishService {

    private static final Logger log = LoggerFactory.getLogger(IfoodCatalogPublishService.class);

    static final int MAX_ATTEMPTS = 3;

    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_DESCRIPTION_LENGTH = 500;

    private static final String STATUS_AVAILABLE = "AVAILABLE";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String CATEGORY_TEMPLATE_DEFAULT = "DEFAULT";

    private final IfoodCatalogWriteClient writeClient;
    private final IfoodTokenService tokenService;
    private final MerchantRepository merchantRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final long retryBackoffMillis;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IfoodCatalogPublishService(IfoodCatalogWriteClient writeClient,
                                      IfoodTokenService tokenService,
                                      MerchantRepository merchantRepository,
                                      ProductRepository productRepository,
                                      CategoryRepository categoryRepository,
                                      @Value("${ifood.retry-backoff-millis:200}") long retryBackoffMillis) {
        this.writeClient = writeClient;
        this.tokenService = tokenService;
        this.merchantRepository = merchantRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    // ------------------------------------------------------------------ publish

    /**
     * Publica produtos como itens do Cardápio Digital. Lista vazia ou nula = todos os
     * produtos {@code ACTIVE} do lojista.
     */
    public IfoodCatalogPublishResult publish(UUID merchantId, List<UUID> productIds) {
        Merchant merchant = requireConnectedMerchant(merchantId);
        String ifoodMerchantId = merchant.getIfoodMerchantId();

        List<ItemOutcome> outcomes = new ArrayList<>();
        // categorias já resolvidas nesta execução — evita um POST /categories por produto
        Map<UUID, String> resolvedCategories = new LinkedHashMap<>();

        for (Product product : selectProducts(merchantId, productIds, outcomes)) {
            outcomes.add(publishProduct(ifoodMerchantId, product, resolvedCategories));
        }

        IfoodCatalogPublishResult result = IfoodCatalogPublishResult.of(outcomes);
        log.info("[iFood] catálogo publicado — merchant {}: {} publicados, {} ignorados",
                merchantId, result.getPublishedProducts(), result.getSkippedProducts());
        return result;
    }

    private ItemOutcome publishProduct(String ifoodMerchantId, Product product,
                                       Map<UUID, String> resolvedCategories) {
        String externalCode = effectiveExternalCode(product);

        String validationError = validateForPublish(product);
        if (validationError != null) {
            return new ItemOutcome(product.getId(), product.getName(), externalCode,
                    Outcome.SKIPPED, validationError);
        }

        try {
            String remoteCategoryId = resolveRemoteCategoryId(
                    ifoodMerchantId, product.getCategory(), resolvedCategories);

            String itemId = product.getIfoodItemId() != null
                    ? product.getIfoodItemId() : UUID.randomUUID().toString();
            String remoteProductId = product.getIfoodProductId() != null
                    ? product.getIfoodProductId() : UUID.randomUUID().toString();
            String whitelabelStatus = product.getStatus() == ProductStatus.ACTIVE
                    ? STATUS_AVAILABLE : STATUS_UNAVAILABLE;

            IfoodCatalogItemRequest request = IfoodCatalogItemRequest.whitelabelItem(
                    itemId, remoteCategoryId, externalCode, product.getPrice(), whitelabelStatus,
                    remoteProductId, product.getName(), productDescription(product));

            execute(ifoodMerchantId, token -> {
                writeClient.upsertItem(token, ifoodMerchantId, request);
                return null;
            });

            product.setIfoodItemId(itemId);
            product.setIfoodProductId(remoteProductId);
            product.setIfoodPublishedAt(LocalDateTime.now());
            productRepository.save(product);

            return new ItemOutcome(product.getId(), product.getName(), externalCode,
                    Outcome.PUBLISHED, null);
        } catch (IfoodUnavailableException e) {
            // iFood fora do ar não é um problema do produto — aborta o lote inteiro (503)
            throw e;
        } catch (RuntimeException e) {
            log.warn("[iFood] falha ao publicar o produto {}: {}", product.getId(), e.toString());
            return new ItemOutcome(product.getId(), product.getName(), externalCode,
                    Outcome.FAILED, failureReason(e));
        }
    }

    /** Cria a categoria no iFood na primeira publicação e guarda o id devolvido. */
    private String resolveRemoteCategoryId(String ifoodMerchantId, Category category,
                                           Map<UUID, String> resolvedCategories) {
        String cached = resolvedCategories.get(category.getId());
        if (cached != null) {
            return cached;
        }
        if (category.getExternalId() != null && !category.getExternalId().isBlank()) {
            resolvedCategories.put(category.getId(), category.getExternalId());
            return category.getExternalId();
        }

        IfoodCatalogCategoryRequest request = new IfoodCatalogCategoryRequest(
                category.getName(), STATUS_AVAILABLE, CATEGORY_TEMPLATE_DEFAULT, 1);
        IfoodCatalogCategoryCreatedResponse created = execute(ifoodMerchantId,
                token -> writeClient.createCategory(token, ifoodMerchantId, request));
        if (created == null || created.id() == null) {
            throw new IfoodBadRequestException("iFood não devolveu o id da categoria criada");
        }

        category.setExternalId(created.id());
        categoryRepository.save(category);
        resolvedCategories.put(category.getId(), created.id());
        return created.id();
    }

    // --------------------------------------------------------------- sync price

    /** Envia os preços atuais do JetMenu numa única chamada em lote. */
    public IfoodCatalogSyncResult syncPrices(UUID merchantId, List<UUID> productIds) {
        Merchant merchant = requireConnectedMerchant(merchantId);
        String ifoodMerchantId = merchant.getIfoodMerchantId();

        List<SkippedProduct> skipped = new ArrayList<>();
        List<IfoodCatalogPriceUpdateRequest.PriceUpdate> updates = new ArrayList<>();

        for (Product product : selectProductsForSync(merchantId, productIds, skipped)) {
            String reason = notPublishedReason(product);
            if (reason == null) {
                reason = validatePrice(product.getPrice());
            }
            if (reason != null) {
                skipped.add(new SkippedProduct(product.getId(), reason));
                continue;
            }
            updates.add(new IfoodCatalogPriceUpdateRequest.PriceUpdate(
                    product.getIfoodProductId(), product.getPrice()));
        }

        if (updates.isEmpty()) {
            return new IfoodCatalogSyncResult(null, 0, skipped);
        }

        IfoodCatalogPriceUpdateRequest request = new IfoodCatalogPriceUpdateRequest(updates);
        String batchId = execute(ifoodMerchantId,
                token -> writeClient.updatePrices(token, ifoodMerchantId, request));
        log.info("[iFood] preços sincronizados — merchant {}: {} itens, lote {}",
                merchantId, updates.size(), batchId);
        return new IfoodCatalogSyncResult(batchId, updates.size(), skipped);
    }

    // -------------------------------------------------------------- sync status

    /** Pausa/reativa itens numa única chamada em lote. */
    public IfoodCatalogSyncResult syncStatus(UUID merchantId, List<IfoodCatalogStatusChange> changes) {
        Merchant merchant = requireConnectedMerchant(merchantId);
        String ifoodMerchantId = merchant.getIfoodMerchantId();

        List<IfoodCatalogStatusChange> requested = changes != null ? changes : List.of();
        Map<UUID, Product> productsById = indexByIdForMerchant(merchantId);

        List<SkippedProduct> skipped = new ArrayList<>();
        List<IfoodCatalogStatusUpdateRequest.StatusUpdate> updates = new ArrayList<>();

        for (IfoodCatalogStatusChange change : requested) {
            Product product = productsById.get(change.productId());
            if (product == null) {
                skipped.add(new SkippedProduct(change.productId(),
                        "Produto não encontrado neste estabelecimento."));
                continue;
            }
            String reason = validateStatus(change.status());
            if (reason == null) {
                reason = notPublishedReason(product);
            }
            if (reason != null) {
                skipped.add(new SkippedProduct(change.productId(), reason));
                continue;
            }
            updates.add(new IfoodCatalogStatusUpdateRequest.StatusUpdate(
                    product.getIfoodItemId(), change.status()));
        }

        if (updates.isEmpty()) {
            return new IfoodCatalogSyncResult(null, 0, skipped);
        }

        IfoodCatalogStatusUpdateRequest request = new IfoodCatalogStatusUpdateRequest(updates);
        String batchId = execute(ifoodMerchantId,
                token -> writeClient.updateStatus(token, ifoodMerchantId, request));
        log.info("[iFood] status sincronizado — merchant {}: {} itens, lote {}",
                merchantId, updates.size(), batchId);
        return new IfoodCatalogSyncResult(batchId, updates.size(), skipped);
    }

    // -------------------------------------------------------------------- batch

    /** Passthrough do acompanhamento de lote — nada é persistido do lado do JetMenu. */
    public IfoodCatalogBatchResponse getBatch(UUID merchantId, String batchId) {
        Merchant merchant = requireConnectedMerchant(merchantId);
        String ifoodMerchantId = merchant.getIfoodMerchantId();
        return execute(ifoodMerchantId, token -> writeClient.getBatch(token, ifoodMerchantId, batchId));
    }

    // --------------------------------------------------------------- validation

    /** @return o motivo em pt-BR, ou {@code null} quando o produto pode ser enviado. */
    private String validateForPublish(Product product) {
        if (product.getCategory() == null) {
            return "Produto sem categoria — defina uma categoria antes de publicar.";
        }
        String nameError = validateName(product.getName(), "do produto");
        if (nameError != null) {
            return nameError;
        }
        String categoryNameError = validateName(product.getCategory().getName(), "da categoria");
        if (categoryNameError != null) {
            return categoryNameError;
        }
        // Product ainda não tem descrição no JetMenu, então nada é enviado em
        // products[].description — a validação fica aqui para o dia em que passar a ter.
        String descriptionError = validateDescription(productDescription(product));
        if (descriptionError != null) {
            return descriptionError;
        }
        return validatePrice(product.getPrice());
    }

    /** JetMenu ainda não guarda descrição de produto — o campo vai ausente no payload. */
    private static String productDescription(Product product) {
        return null;
    }

    private static String validateName(String name, String subject) {
        if (name == null || name.isBlank()) {
            return "Nome " + subject + " está vazio.";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "Nome " + subject + " passa de " + MAX_NAME_LENGTH
                    + " caracteres aceitos pelo iFood.";
        }
        return null;
    }

    private static String validateDescription(String description) {
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            return "Descrição passa de " + MAX_DESCRIPTION_LENGTH
                    + " caracteres aceitos pelo iFood.";
        }
        return null;
    }

    private static String validatePrice(BigDecimal price) {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return "Produto sem preço válido — o iFood exige um preço maior que zero.";
        }
        return null;
    }

    private static String validateStatus(String status) {
        if (!STATUS_AVAILABLE.equals(status) && !STATUS_UNAVAILABLE.equals(status)) {
            return "Status inválido — use AVAILABLE ou UNAVAILABLE.";
        }
        return null;
    }

    private static String notPublishedReason(Product product) {
        if (product.getIfoodItemId() == null || product.getIfoodProductId() == null) {
            return "Produto ainda não foi publicado no iFood.";
        }
        return null;
    }

    // ------------------------------------------------------------- product load

    private Merchant requireConnectedMerchant(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .filter(m -> m.getIfoodMerchantId() != null && !m.getIfoodMerchantId().isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + merchantId + " is not connected to iFood"));
    }

    private Map<UUID, Product> indexByIdForMerchant(UUID merchantId) {
        Map<UUID, Product> byId = new LinkedHashMap<>();
        for (Product product : productRepository.findAllByMerchantId(merchantId)) {
            byId.put(product.getId(), product);
        }
        return byId;
    }

    /** Ids desconhecidos entram no relatório como {@code FAILED} em vez de sumirem. */
    private List<Product> selectProducts(UUID merchantId, List<UUID> productIds,
                                         List<ItemOutcome> outcomes) {
        Map<UUID, Product> byId = indexByIdForMerchant(merchantId);
        if (productIds == null || productIds.isEmpty()) {
            return byId.values().stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .toList();
        }

        List<Product> selected = new ArrayList<>();
        for (UUID productId : productIds) {
            Product product = byId.get(productId);
            if (product == null) {
                outcomes.add(new ItemOutcome(productId, null, null, Outcome.FAILED,
                        "Produto não encontrado neste estabelecimento."));
                continue;
            }
            selected.add(product);
        }
        return selected;
    }

    private List<Product> selectProductsForSync(UUID merchantId, List<UUID> productIds,
                                                List<SkippedProduct> skipped) {
        Map<UUID, Product> byId = indexByIdForMerchant(merchantId);
        if (productIds == null || productIds.isEmpty()) {
            return byId.values().stream()
                    .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                    .toList();
        }

        List<Product> selected = new ArrayList<>();
        for (UUID productId : productIds) {
            Product product = byId.get(productId);
            if (product == null) {
                skipped.add(new SkippedProduct(productId,
                        "Produto não encontrado neste estabelecimento."));
                continue;
            }
            selected.add(product);
        }
        return selected;
    }

    /** Nunca sobrescreve o {@code externalId} do lojista; só deriva um código estável. */
    private static String effectiveExternalCode(Product product) {
        String externalId = product.getExternalId();
        if (externalId != null && !externalId.isBlank()) {
            return externalId;
        }
        return "MB-" + product.getId();
    }

    // --------------------------------------------------------------- resilience

    private <T> T execute(String ifoodMerchantId, Function<String, T> operation) {
        RuntimeException lastTransient = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callOnce(operation);
            } catch (HttpClientErrorException e) {
                // 4xx é condição de cliente — nunca repetir, traduzir e falhar de imediato
                throw translate(e);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastTransient = e;
                log.warn("[iFood] falha transitória no Catalog do merchant {} (tentativa {}/{}): {}",
                        ifoodMerchantId, attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw new IfoodUnavailableException(lastTransient);
    }

    private <T> T callOnce(Function<String, T> operation) {
        try {
            return operation.apply(tokenService.getAccessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("[iFood] 401 recebido — forçando refresh do token e repetindo a chamada");
            return operation.apply(tokenService.handleUnauthorized());
        }
    }

    private RuntimeException translate(HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            case 400, 422 -> new IfoodBadRequestException(extractDetail(e));
            case 404 -> new IfoodResourceNotFoundException();
            case 409 -> new IfoodCatalogConflictException(extractDetail(e));
            default -> e;
        };
    }

    private static String failureReason(RuntimeException e) {
        if (e instanceof IfoodResourceNotFoundException) {
            return "Item ou categoria não encontrado no iFood.";
        }
        if (e instanceof IfoodCatalogConflictException conflict) {
            return "Conflito no iFood: " + conflict.getDetail();
        }
        if (e instanceof IfoodBadRequestException badRequest) {
            return "O iFood recusou os dados: " + badRequest.getDetail();
        }
        return "Falha inesperada ao falar com o iFood. Tente novamente.";
    }

    private String extractDetail(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getStatusText();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : List.of("message", "error", "detail")) {
                JsonNode node = root.path(field);
                if (node.isTextual() && !node.asText().isBlank()) {
                    return node.asText();
                }
            }
        } catch (Exception ignored) {
            // corpo não é JSON — devolve o payload cru abaixo
        }
        return body;
    }

    private void backoff(int attempt) {
        if (retryBackoffMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMillis * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off iFood retry", e);
        }
    }
}
