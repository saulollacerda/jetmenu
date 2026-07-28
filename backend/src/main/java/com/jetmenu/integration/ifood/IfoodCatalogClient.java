package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Read-only client for the iFood Catalog API v2.0 — JetMenu only imports the
 * catalog, never writes back (no item creation, price sync or availability).
 */
@Component
public class IfoodCatalogClient {

    private final RestClient restClient;

    public IfoodCatalogClient(
            RestClient.Builder builder,
            @Value("${ifood.catalog-base-url}") String catalogBaseUrl) {
        this.restClient = builder.baseUrl(catalogBaseUrl).build();
    }

    public List<IfoodCatalogResponse> listCatalogs(String accessToken, String ifoodMerchantId) {
        List<IfoodCatalogResponse> catalogs = restClient.get()
                .uri("/merchants/{merchantId}/catalogs", ifoodMerchantId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return catalogs != null ? catalogs : List.of();
    }

    public List<IfoodCatalogCategoryResponse> listCategories(String accessToken,
                                                             String ifoodMerchantId,
                                                             String catalogId) {
        List<IfoodCatalogCategoryResponse> categories = restClient.get()
                .uri("/merchants/{merchantId}/catalogs/{catalogId}/categories?includeItems=true",
                        ifoodMerchantId, catalogId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return categories != null ? categories : List.of();
    }
}
