package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodCatalogCategoryResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogResponse;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodCatalogClient")
class IfoodCatalogClientTest {

    private static final String BASE_URL = "https://merchant-api.ifood.com.br/catalog/v2.0";

    private MockRestServiceServer server;
    private IfoodCatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodCatalogClient(builder, BASE_URL);
    }

    @Test
    @DisplayName("listCatalogs envia Bearer token e retorna catálogos com contexto")
    void listCatalogs_shouldSendAuthHeaderAndReturnCatalogs() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/catalogs"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      [
                        {"catalogId":"cat-default","context":["DEFAULT"],"status":"AVAILABLE",
                         "modifiedAt":1597350642.71608},
                        {"catalogId":"cat-indoor","context":["INDOOR"],"status":"AVAILABLE"}
                      ]
                      """, MediaType.APPLICATION_JSON));

        List<IfoodCatalogResponse> catalogs = client.listCatalogs("access.jwt", "ifood-m1");

        assertThat(catalogs).hasSize(2);
        assertThat(catalogs.get(0).getCatalogId()).isEqualTo("cat-default");
        assertThat(catalogs.get(0).getContext()).containsExactly("DEFAULT");
        assertThat(catalogs.get(1).getContext()).containsExactly("INDOOR");
        server.verify();
    }

    @Test
    @DisplayName("listCatalogs retorna lista vazia quando o corpo é nulo")
    void listCatalogs_shouldReturnEmptyListOnNullBody() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/catalogs"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess());

        List<IfoodCatalogResponse> catalogs = client.listCatalogs("access.jwt", "ifood-m1");

        assertThat(catalogs).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("listCategories busca categorias com itens, preço e contextModifiers")
    void listCategories_shouldReturnCategoriesWithItems() {
        server.expect(requestTo(
                        BASE_URL + "/merchants/ifood-m1/catalogs/cat-default/categories?includeItems=true"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      [
                        {
                          "id":"c1","name":"Lanches","status":"AVAILABLE","template":"DEFAULT",
                          "sequence":1,
                          "items":[
                            {"id":"i1","name":"X-Burger","description":"Pão, carne e queijo",
                             "externalCode":"BURGER_001","status":"AVAILABLE","sequence":1,
                             "price":{"value":25.00,"originalValue":30.00},
                             "contextModifiers":[
                               {"catalogContext":"WHITELABEL","price":{"value":28.00},
                                "status":"UNAVAILABLE"}
                             ]}
                          ]
                        },
                        {"id":"c2","name":"Bebidas","status":"AVAILABLE","template":"DEFAULT","items":[]}
                      ]
                      """, MediaType.APPLICATION_JSON));

        List<IfoodCatalogCategoryResponse> categories =
                client.listCategories("access.jwt", "ifood-m1", "cat-default");

        assertThat(categories).hasSize(2);
        IfoodCatalogCategoryResponse lanches = categories.get(0);
        assertThat(lanches.getId()).isEqualTo("c1");
        assertThat(lanches.getName()).isEqualTo("Lanches");
        assertThat(lanches.getItems()).hasSize(1);

        IfoodCatalogCategoryResponse.Item burger = lanches.getItems().get(0);
        assertThat(burger.getName()).isEqualTo("X-Burger");
        assertThat(burger.getExternalCode()).isEqualTo("BURGER_001");
        assertThat(burger.getPrice().getValue()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(burger.getContextModifiers()).hasSize(1);
        assertThat(burger.getContextModifiers().get(0).getCatalogContext()).isEqualTo("WHITELABEL");
        assertThat(burger.getContextModifiers().get(0).getPrice().getValue())
                .isEqualByComparingTo(new BigDecimal("28.00"));
        server.verify();
    }

    @Test
    @DisplayName("listCategories ignora campos desconhecidos do payload")
    void listCategories_shouldIgnoreUnknownFields() {
        server.expect(requestTo(
                        BASE_URL + "/merchants/ifood-m1/catalogs/cat-default/categories?includeItems=true"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess("""
                      [
                        {"id":"c1","name":"Pizzas","template":"PIZZA","unknownField":{"x":1},
                         "items":[
                           {"id":"i1","name":"Calabresa","futureField":"y",
                            "productInfo":{"quantity":1}}
                         ]}
                      ]
                      """, MediaType.APPLICATION_JSON));

        List<IfoodCatalogCategoryResponse> categories =
                client.listCategories("access.jwt", "ifood-m1", "cat-default");

        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getItems().get(0).getName()).isEqualTo("Calabresa");
        assertThat(categories.get(0).getItems().get(0).getPrice()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("listCategories retorna lista vazia quando o corpo é nulo")
    void listCategories_shouldReturnEmptyListOnNullBody() {
        server.expect(requestTo(
                        BASE_URL + "/merchants/ifood-m1/catalogs/cat-default/categories?includeItems=true"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess());

        List<IfoodCatalogCategoryResponse> categories =
                client.listCategories("access.jwt", "ifood-m1", "cat-default");

        assertThat(categories).isEmpty();
        server.verify();
    }

    // ------------------------------------------------------------------------------------
    // Leituras cruas — usadas pela tela de diagnóstico da homologação, que precisa mostrar
    // a resposta do iFood exatamente como veio, incluindo erros.
    // ------------------------------------------------------------------------------------

    @Test
    @DisplayName("rawCatalogs devolve URL, status e corpo sem desserializar")
    void rawCatalogs_shouldReturnUntouchedBody() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/catalogs"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      [{"catalogId":"cat-default","context":["DEFAULT"]}]
                      """, MediaType.APPLICATION_JSON));

        IfoodRawResponse raw = client.rawCatalogs("access.jwt", "ifood-m1");

        assertThat(raw.endpoint()).isEqualTo(BASE_URL + "/merchants/ifood-m1/catalogs");
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.body()).contains("\"catalogId\":\"cat-default\"");
        server.verify();
    }

    @Test
    @DisplayName("rawCategories pede itens junto e devolve o corpo cru")
    void rawCategories_shouldRequestItemsAndReturnUntouchedBody() {
        server.expect(requestTo(
                        BASE_URL + "/merchants/ifood-m1/catalogs/cat-default/categories?includeItems=true"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      [{"id":"c1","name":"Lanches","items":[{"id":"i1","name":"X-Burger"}]}]
                      """, MediaType.APPLICATION_JSON));

        IfoodRawResponse raw = client.rawCategories("access.jwt", "ifood-m1", "cat-default");

        assertThat(raw.endpoint())
                .isEqualTo(BASE_URL + "/merchants/ifood-m1/catalogs/cat-default/categories?includeItems=true");
        assertThat(raw.status()).isEqualTo(200);
        assertThat(raw.body()).contains("X-Burger");
        server.verify();
    }

    @Test
    @DisplayName("leitura crua devolve o erro do iFood em vez de lançar exceção")
    void rawCatalogs_shouldReturnErrorBodyInsteadOfThrowing() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/catalogs"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.FORBIDDEN)
                      .contentType(MediaType.APPLICATION_JSON)
                      .body("""
                            {"error":{"code":"403","message":"Merchant not allowed"}}
                            """));

        IfoodRawResponse raw = client.rawCatalogs("access.jwt", "ifood-m1");

        assertThat(raw.status()).isEqualTo(403);
        assertThat(raw.body()).contains("Merchant not allowed");
        server.verify();
    }

    @Test
    @DisplayName("leitura crua devolve corpo vazio quando o iFood não manda conteúdo")
    void rawCatalogs_shouldReturnEmptyBodyWhenNoContent() {
        server.expect(requestTo(BASE_URL + "/merchants/ifood-m1/catalogs"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.NO_CONTENT));

        IfoodRawResponse raw = client.rawCatalogs("access.jwt", "ifood-m1");

        assertThat(raw.status()).isEqualTo(204);
        assertThat(raw.body()).isEmpty();
        server.verify();
    }
}
