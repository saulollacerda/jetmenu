package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryCreatedResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogItemRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPriceUpdateRequest;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodCatalogWriteClient")
class IfoodCatalogWriteClientTest {

    private static final String BASE_URL = "https://merchant-api.ifood.com.br/catalog/v2.0";

    private MockRestServiceServer server;
    private IfoodCatalogWriteClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodCatalogWriteClient(builder, BASE_URL);
    }

    @Test
    @DisplayName("createCategory posta a categoria e devolve o id gerado pelo iFood")
    void createCategory_shouldPostAndReturnCreatedCategory() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/categories"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(content().json("""
                      {"name":"Lanches","status":"AVAILABLE","template":"DEFAULT","index":1}
                      """, true))
              .andRespond(withSuccess("""
                      {"id":"cat-remote-1","name":"Lanches","status":"AVAILABLE","template":"DEFAULT"}
                      """, MediaType.APPLICATION_JSON));

        IfoodCatalogCategoryCreatedResponse created = client.createCategory("access.jwt", "ifood-m1",
                new IfoodCatalogCategoryRequest("Lanches", "AVAILABLE", "DEFAULT", 1));

        assertThat(created.id()).isEqualTo("cat-remote-1");
        assertThat(created.name()).isEqualTo("Lanches");
        server.verify();
    }

    @Test
    @DisplayName("upsertItem envia o item com o preço/status reais no contextModifier WHITELABEL")
    void upsertItem_shouldSendWhitelabelContextModifier() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items"))
              .andExpect(method(HttpMethod.PUT))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(content().json("""
                      {
                        "item": {
                          "id":"item-1","type":"DEFAULT","categoryId":"cat-remote-1",
                          "status":"UNAVAILABLE","price":{"value":25.00},"externalCode":"JM-1",
                          "contextModifiers":[
                            {"catalogContext":"WHITELABEL","price":{"value":25.00},
                             "status":"AVAILABLE","externalCode":"JM-1"}
                          ]
                        },
                        "products":[{"id":"prod-1","name":"X-Burger"}],
                        "optionGroups":[],
                        "options":[]
                      }
                      """, true))
              .andRespond(withSuccess());

        client.upsertItem("access.jwt", "ifood-m1", IfoodCatalogItemRequest.whitelabelItem(
                "item-1", "cat-remote-1", "JM-1", new BigDecimal("25.00"), "AVAILABLE",
                "prod-1", "X-Burger", null));

        server.verify();
    }

    @Test
    @DisplayName("upsertItem propaga 404 do iFood sem engolir o erro")
    void upsertItem_shouldPropagateNotFound() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items"))
              .andExpect(method(HttpMethod.PUT))
              .andRespond(withResourceNotFound());

        assertThatThrownBy(() -> client.upsertItem("access.jwt", "ifood-m1",
                IfoodCatalogItemRequest.whitelabelItem("item-1", "cat-1", "JM-1",
                        BigDecimal.ONE, "AVAILABLE", "prod-1", "X", null)))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
        server.verify();
    }

    @Test
    @DisplayName("updatePrices envia preços como número simples e devolve o batchId")
    void updatePrices_shouldSendFlatPricesAndReturnBatchId() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items/price"))
              .andExpect(method(HttpMethod.PATCH))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(content().json("""
                      {"prices":[{"productId":"prod-1","price":26.50},
                                 {"productId":"prod-2","price":8.00}]}
                      """, true))
              .andRespond(withSuccess("{\"batchId\":\"batch-123\"}", MediaType.APPLICATION_JSON));

        String batchId = client.updatePrices("access.jwt", "ifood-m1",
                new IfoodCatalogPriceUpdateRequest(List.of(
                        new IfoodCatalogPriceUpdateRequest.PriceUpdate("prod-1", new BigDecimal("26.50")),
                        new IfoodCatalogPriceUpdateRequest.PriceUpdate("prod-2", new BigDecimal("8.00")))));

        assertThat(batchId).isEqualTo("batch-123");
        server.verify();
    }

    @Test
    @DisplayName("updateStatus envia os itens em lote e devolve o batchId")
    void updateStatus_shouldSendItemsAndReturnBatchId() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items/status"))
              .andExpect(method(HttpMethod.PATCH))
              .andExpect(content().json("""
                      {"items":[{"id":"item-1","status":"UNAVAILABLE"}]}
                      """, true))
              .andRespond(withSuccess("{\"batchId\":\"batch-9\"}", MediaType.APPLICATION_JSON));

        String batchId = client.updateStatus("access.jwt", "ifood-m1",
                new IfoodCatalogStatusUpdateRequest(List.of(
                        new IfoodCatalogStatusUpdateRequest.StatusUpdate("item-1", "UNAVAILABLE"))));

        assertThat(batchId).isEqualTo("batch-9");
        server.verify();
    }

    @Test
    @DisplayName("updateStatus propaga 409 de conflito do iFood")
    void updateStatus_shouldPropagateConflict() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items/status"))
              .andExpect(method(HttpMethod.PATCH))
              .andRespond(withStatus(org.springframework.http.HttpStatus.CONFLICT)
                      .body("{\"code\":\"CONFLICT\",\"message\":\"item em edição\"}")
                      .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.updateStatus("access.jwt", "ifood-m1",
                new IfoodCatalogStatusUpdateRequest(List.of(
                        new IfoodCatalogStatusUpdateRequest.StatusUpdate("item-1", "AVAILABLE")))))
                .isInstanceOf(HttpClientErrorException.Conflict.class);
        server.verify();
    }

    @Test
    @DisplayName("getBatch devolve o resultado consolidado do lote")
    void getBatch_shouldReturnBatchResult() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/batch/batch-123"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      {"batchId":"batch-123","status":"COMPLETED","successCount":2,"failureCount":1,
                       "results":[{"resourceId":"prod-1","result":"SUCCESS"},
                                  {"resourceId":"prod-2","result":"SUCCESS"},
                                  {"resourceId":"prod-3","result":"ERROR"}]}
                      """, MediaType.APPLICATION_JSON));

        IfoodCatalogBatchResponse batch = client.getBatch("access.jwt", "ifood-m1", "batch-123");

        assertThat(batch.batchId()).isEqualTo("batch-123");
        assertThat(batch.status()).isEqualTo("COMPLETED");
        assertThat(batch.successCount()).isEqualTo(2);
        assertThat(batch.failureCount()).isEqualTo(1);
        assertThat(batch.results()).hasSize(3);
        assertThat(batch.results().get(2).resourceId()).isEqualTo("prod-3");
        assertThat(batch.results().get(2).result()).isEqualTo("ERROR");
        server.verify();
    }

    @Test
    @DisplayName("updatePrices devolve null quando o iFood responde sem corpo")
    void updatePrices_shouldReturnNullWhenBodyIsEmpty() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/items/price"))
              .andExpect(method(HttpMethod.PATCH))
              .andRespond(withSuccess());

        String batchId = client.updatePrices("access.jwt", "ifood-m1",
                new IfoodCatalogPriceUpdateRequest(List.of(
                        new IfoodCatalogPriceUpdateRequest.PriceUpdate("prod-1", BigDecimal.ONE))));

        assertThat(batchId).isNull();
        server.verify();
    }
}
