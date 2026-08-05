package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    // ------------------------------------------------------------------------------------
    // Leituras cruas — a tela de diagnóstico da homologação mostra a resposta do iFood como
    // ela chega. Erros não viram exceção aqui: um 403 do iFood é justamente o que o analista
    // precisa ver na tela, então status e corpo são devolvidos igual aos casos de sucesso.
    // ------------------------------------------------------------------------------------

    public IfoodRawResponse rawCatalogs(String accessToken, String ifoodMerchantId) {
        return rawGet(accessToken, "/merchants/{merchantId}/catalogs", ifoodMerchantId);
    }

    public IfoodRawResponse rawCategories(String accessToken, String ifoodMerchantId,
                                          String catalogId) {
        return rawGet(accessToken,
                "/merchants/{merchantId}/catalogs/{catalogId}/categories?includeItems=true",
                ifoodMerchantId, catalogId);
    }

    private IfoodRawResponse rawGet(String accessToken, String uriTemplate, Object... uriVariables) {
        return restClient.get()
                .uri(uriTemplate, uriVariables)
                .header("Authorization", "Bearer " + accessToken)
                .exchange((request, response) -> new IfoodRawResponse(
                        request.getURI().toString(),
                        response.getStatusCode().value(),
                        readBody(response)), false);
    }

    /** Corpo vazio (204, ou resposta sem conteúdo) vira string vazia, nunca null. */
    private static String readBody(ClientHttpResponse response) throws IOException {
        try (InputStream body = response.getBody()) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
