package com.jetmenu.integration.anotaai;

import com.jetmenu.integration.RawJsonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("AnotaAIClient")
class AnotaAIClientTest {

    private static final String ORDERS_URL = "https://api-parceiros.anota.ai";
    private static final String MENU_URL = "https://api-menu.anota.ai";

    private MockRestServiceServer server;
    private AnotaAIClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnotaAIClient(builder, ORDERS_URL, MENU_URL);
    }

    @Test
    @DisplayName("getOrderDetail retorna DTO parseado junto com o JSON bruto da resposta")
    void getOrderDetail_shouldReturnParsedBodyAndRawJson() {
        String responseJson = """
                {
                  "success": true,
                  "info": {
                    "_id": "ord-1",
                    "createdAt": "2026-07-01T18:00:00Z",
                    "total": 25.8,
                    "deliveryFee": 6.0,
                    "additionalFees": [{"amount": 1.99, "tag": "serviceFee"}]
                  }
                }
                """;
        server.expect(requestTo(ORDERS_URL + "/partnerauth/ping/get/ord-1"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header(HttpHeaders.AUTHORIZATION, "key-1"))
              .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        RawJsonResponse<AnotaAIOrderDetailResponse> result =
                client.getOrderDetail("key-1", "ord-1");

        assertThat(result.body().getInfo().getId()).isEqualTo("ord-1");
        assertThat(result.body().getInfo().getTotal()).isEqualTo(25.8);
        // Campos ignorados pelo DTO (ex.: additionalFees) precisam sobreviver no raw —
        // é exatamente o que a auditoria financeira quer inspecionar depois.
        assertThat(result.rawJson()).contains("additionalFees").contains("serviceFee");
        server.verify();
    }

    @Test
    @DisplayName("getOrderDetail retorna null quando o corpo da resposta vem vazio")
    void getOrderDetail_shouldReturnNullOnEmptyBody() {
        server.expect(requestTo(ORDERS_URL + "/partnerauth/ping/get/ord-2"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThat(client.getOrderDetail("key-1", "ord-2")).isNull();
        server.verify();
    }
}
