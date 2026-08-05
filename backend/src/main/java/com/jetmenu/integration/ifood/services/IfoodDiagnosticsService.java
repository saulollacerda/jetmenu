package com.jetmenu.integration.ifood.services;

import com.jetmenu.integration.ifood.IfoodCatalogClient;
import com.jetmenu.integration.ifood.IfoodDiagnosticsNotAllowedException;
import com.jetmenu.integration.ifood.IfoodResourceNotFoundException;
import com.jetmenu.integration.ifood.IfoodTokenService;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Leituras cruas do catálogo do iFood para a tela de diagnóstico da homologação.
 *
 * <p>A homologação do módulo Catalog exige demonstrar ao vivo, item a item do checklist,
 * chamadas isoladas como {@code GET /catalogs} e a listagem de itens. Este serviço existe
 * só para isso: não importa, não grava e não transforma nada — devolve o que o iFood
 * respondeu.</p>
 *
 * <p>Acesso restrito por {@code ifood.diagnostics-merchant-ids}: uma lista separada por
 * vírgulas que aceita tanto o id do merchant no JetMenu (UUID) quanto o id dele no iFood,
 * porque na prática os dois circulam. Lista vazia (o default) mantém a tela fechada para
 * todo mundo, então subir o código em produção não expõe nada por si só.</p>
 */
@Service
public class IfoodDiagnosticsService {

    private static final String DEFAULT_CONTEXT = "DEFAULT";

    private final IfoodCatalogClient catalogClient;
    private final IfoodTokenService tokenService;
    private final MerchantRepository merchantRepository;
    private final Set<String> allowedMerchantIds;

    public IfoodDiagnosticsService(IfoodCatalogClient catalogClient,
                                   IfoodTokenService tokenService,
                                   MerchantRepository merchantRepository,
                                   @Value("${ifood.diagnostics-merchant-ids:}") String diagnosticsMerchantIds) {
        this.catalogClient = catalogClient;
        this.tokenService = tokenService;
        this.merchantRepository = merchantRepository;
        this.allowedMerchantIds = parseWhitelist(diagnosticsMerchantIds);
    }

    private static Set<String> parseWhitelist(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(id -> id.toLowerCase())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Usado pela UI para decidir se mostra a entrada do diagnóstico — nunca lança. */
    public boolean isEnabledFor(UUID merchantId) {
        if (allowedMerchantIds.isEmpty()) return false;
        return merchantRepository.findById(merchantId)
                .map(this::isAllowed)
                .orElse(false);
    }

    private boolean isAllowed(Merchant merchant) {
        if (merchant.getId() != null
                && allowedMerchantIds.contains(merchant.getId().toString().toLowerCase())) {
            return true;
        }
        String ifoodMerchantId = merchant.getIfoodMerchantId();
        return ifoodMerchantId != null
                && allowedMerchantIds.contains(ifoodMerchantId.toLowerCase());
    }

    /** {@code GET /merchants/{merchantId}/catalogs}, cru. */
    public IfoodRawResponse listCatalogs(UUID merchantId) {
        String ifoodMerchantId = connectedIfoodMerchantId(merchantId);
        return catalogClient.rawCatalogs(tokenService.getAccessToken(), ifoodMerchantId);
    }

    /**
     * Listagem de itens, crua. A Catalog v2.0 não tem um {@code GET /items} avulso: os itens
     * vêm pendurados nas categorias, via {@code categories?includeItems=true}. Sem
     * {@code catalogId} informado, resolvemos o catálogo DEFAULT (o mesmo critério da
     * importação) para que o botão funcione com um clique só.
     */
    public IfoodRawResponse listItems(UUID merchantId, String catalogId) {
        String ifoodMerchantId = connectedIfoodMerchantId(merchantId);
        String accessToken = tokenService.getAccessToken();
        String resolvedCatalogId = catalogId != null && !catalogId.isBlank()
                ? catalogId
                : resolveDefaultCatalogId(accessToken, ifoodMerchantId);
        return catalogClient.rawCategories(accessToken, ifoodMerchantId, resolvedCatalogId);
    }

    private String resolveDefaultCatalogId(String accessToken, String ifoodMerchantId) {
        List<IfoodCatalogResponse> catalogs = catalogClient.listCatalogs(accessToken, ifoodMerchantId);
        return pickCatalogId(catalogs).orElseThrow(IfoodResourceNotFoundException::new);
    }

    private static Optional<String> pickCatalogId(List<IfoodCatalogResponse> catalogs) {
        return catalogs.stream()
                .filter(c -> c.getContext() != null && c.getContext().contains(DEFAULT_CONTEXT))
                .map(IfoodCatalogResponse::getCatalogId)
                .findFirst()
                .or(() -> catalogs.stream().map(IfoodCatalogResponse::getCatalogId).findFirst());
    }

    /** A whitelist é checada antes de qualquer chamada ao iFood. */
    private String connectedIfoodMerchantId(UUID merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .filter(this::isAllowed)
                .orElseThrow(IfoodDiagnosticsNotAllowedException::new);
        String ifoodMerchantId = merchant.getIfoodMerchantId();
        if (ifoodMerchantId == null) {
            throw new IllegalStateException("Merchant " + merchantId + " is not connected to iFood");
        }
        return ifoodMerchantId;
    }
}
