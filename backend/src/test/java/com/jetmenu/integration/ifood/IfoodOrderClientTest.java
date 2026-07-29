package com.jetmenu.integration.ifood;

import com.jetmenu.integration.RawJsonResponse;
import com.jetmenu.integration.ifood.dto.IfoodEventResponse;
import com.jetmenu.integration.ifood.dto.IfoodOrderDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodOrderClient")
class IfoodOrderClientTest {

    private static final String BASE_URL = "https://merchant-api.ifood.com.br/order/v1.0";

    private MockRestServiceServer server;
    private IfoodOrderClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodOrderClient(builder, BASE_URL);
    }

    @Test
    @DisplayName("pollEvents envia Bearer token e x-polling-merchants e retorna eventos")
    void pollEvents_shouldSendAuthAndMerchantsHeaderAndReturnEvents() {
        server.expect(requestTo(BASE_URL + "/events:polling"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(header("x-polling-merchants", "merchant-1,merchant-2"))
              .andRespond(withSuccess("""
                      [
                        {"id":"evt_1","code":"CON","fullCode":"CONCLUDED",
                         "orderId":"ord_1","merchantId":"merchant-1","createdAt":"2024-04-25T19:00:00Z"},
                        {"id":"evt_2","code":"PLC","fullCode":"PLACED",
                         "orderId":"ord_2","merchantId":"merchant-1","createdAt":"2024-04-25T18:00:00Z"}
                      ]
                      """, MediaType.APPLICATION_JSON));

        List<IfoodEventResponse> events = client.pollEvents("access.jwt", List.of("merchant-1", "merchant-2"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getId()).isEqualTo("evt_1");
        assertThat(events.get(0).getFullCode()).isEqualTo("CONCLUDED");
        assertThat(events.get(0).getOrderId()).isEqualTo("ord_1");
        server.verify();
    }

    @Test
    @DisplayName("pollEvents aceita resposta com wrapper {\"events\":[...]} (variação da API)")
    void pollEvents_shouldAcceptEventsWrapperShape() {
        server.expect(requestTo(BASE_URL + "/events:polling"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess("""
                      {"events":[
                        {"id":"evt_9","code":"CONCLUDED","fullCode":"ORDER_CONCLUDED",
                         "orderId":"ord_9","createdAt":"2024-04-25T19:00:00Z"}
                      ]}
                      """, MediaType.APPLICATION_JSON));

        List<IfoodEventResponse> events = client.pollEvents("access.jwt", List.of("merchant-1"));

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getId()).isEqualTo("evt_9");
        server.verify();
    }

    @Test
    @DisplayName("pollEvents retorna lista vazia quando iFood responde 204 (sem eventos)")
    void pollEvents_shouldReturnEmptyListOnNoContent() {
        server.expect(requestTo(BASE_URL + "/events:polling"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.NO_CONTENT));

        List<IfoodEventResponse> events = client.pollEvents("access.jwt", List.of("merchant-1"));

        assertThat(events).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("acknowledgeEvents envia POST com array de ids")
    void acknowledgeEvents_shouldPostAcknowledgedEventIds() {
        server.expect(requestTo(BASE_URL + "/events/acknowledgment"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
              .andExpect(jsonPath("$[0].id").value("evt_1"))
              .andExpect(jsonPath("$[1].id").value("evt_2"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.acknowledgeEvents("access.jwt", List.of("evt_1", "evt_2"));

        server.verify();
    }

    @Test
    @DisplayName("acknowledgeEvents quebra a lista em lotes de no máximo 2000 ids")
    void acknowledgeEvents_shouldSplitIdsIntoBatchesOfAtMostTwoThousand() {
        List<String> eventIds = IntStream.range(0, 2500).mapToObj(i -> "evt_" + i).toList();

        server.expect(requestTo(BASE_URL + "/events/acknowledgment"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(jsonPath("$.length()").value(2000))
              .andExpect(jsonPath("$[0].id").value("evt_0"))
              .andExpect(jsonPath("$[1999].id").value("evt_1999"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));
        server.expect(requestTo(BASE_URL + "/events/acknowledgment"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(jsonPath("$.length()").value(500))
              .andExpect(jsonPath("$[0].id").value("evt_2000"))
              .andExpect(jsonPath("$[499].id").value("evt_2499"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.acknowledgeEvents("access.jwt", eventIds);

        server.verify();
    }

    @Test
    @DisplayName("acknowledgeEvents envia uma única chamada quando a lista cabe em um lote")
    void acknowledgeEvents_shouldSendSingleRequestWhenListFitsInOneBatch() {
        List<String> eventIds = IntStream.range(0, 2000).mapToObj(i -> "evt_" + i).toList();

        server.expect(ExpectedCount.once(), requestTo(BASE_URL + "/events/acknowledgment"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(jsonPath("$.length()").value(2000))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.acknowledgeEvents("access.jwt", eventIds);

        server.verify();
    }

    @Test
    @DisplayName("acknowledgeEvents não chama a API quando não há ids")
    void acknowledgeEvents_shouldNotCallApiWhenThereAreNoIds() {
        client.acknowledgeEvents("access.jwt", List.of());

        server.verify();
    }

    @Test
    @DisplayName("getOrderDetail retorna pedido completo mapeado")
    void getOrderDetail_shouldReturnFullOrderDetail() {
        server.expect(requestTo(BASE_URL + "/orders/ord_1"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      {
                        "id":"ord_1",
                        "displayId":"1234",
                        "orderType":"DELIVERY",
                        "orderTiming":"IMMEDIATE",
                        "salesChannel":"IFOOD",
                        "category":"FOOD",
                        "createdAt":"2024-04-25T18:00:00Z",
                        "isTest":false,
                        "extraInfo":"Pago Online. NAO LEVAR MAQUINA",
                        "merchant":{"id":"ifood-merchant-1","name":"Loja Exemplo"},
                        "customer":{
                          "id":"cust-1","name":"Maria Santos",
                          "phone":{"number":"0800 123 4567","localizer":"12345678"}
                        },
                        "items":[
                          {
                            "index":0,
                            "id":"item-1",
                            "externalCode":"2331",
                            "name":"Acai 500 ml",
                            "quantity":2,
                            "unit":"UN",
                            "unitPrice":21.99,
                            "optionsPrice":3.0,
                            "totalPrice":46.98,
                            "observations":"Sem granola",
                            "options":[
                              {"index":0,"name":"Morango","groupName":"Adicionais",
                               "externalCode":"","quantity":1,"unitPrice":1.5,
                               "addition":0,"price":1.5}
                            ]
                          }
                        ],
                        "total":{
                          "subTotal":46.98,
                          "deliveryFee":5.99,
                          "additionalFees":0,
                          "benefits":0,
                          "orderAmount":52.97
                        }
                      }
                      """, MediaType.APPLICATION_JSON));

        RawJsonResponse<IfoodOrderDetailResponse> result = client.getOrderDetail("access.jwt", "ord_1");
        IfoodOrderDetailResponse detail = result.body();

        assertThat(result.rawJson()).contains("\"displayId\":\"1234\"");

        assertThat(detail.getId()).isEqualTo("ord_1");
        assertThat(detail.getCategory()).isEqualTo("FOOD");
        assertThat(detail.isTest()).isFalse();
        assertThat(detail.getExtraInfo()).isEqualTo("Pago Online. NAO LEVAR MAQUINA");
        assertThat(detail.getMerchant().getId()).isEqualTo("ifood-merchant-1");
        assertThat(detail.getCustomer().getName()).isEqualTo("Maria Santos");
        assertThat(detail.getCustomer().getPhone().getNumber()).isEqualTo("0800 123 4567");
        assertThat(detail.getItems()).hasSize(1);
        assertThat(detail.getItems().get(0).getExternalCode()).isEqualTo("2331");
        assertThat(detail.getItems().get(0).getName()).isEqualTo("Acai 500 ml");
        assertThat(detail.getItems().get(0).getQuantity()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(detail.getItems().get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("21.99"));
        assertThat(detail.getItems().get(0).getOptions()).hasSize(1);
        assertThat(detail.getItems().get(0).getOptions().get(0).getName()).isEqualTo("Morango");
        assertThat(detail.getTotal().getOrderAmount()).isEqualByComparingTo(new BigDecimal("52.97"));
        assertThat(detail.getTotal().getDeliveryFee()).isEqualByComparingTo(new BigDecimal("5.99"));
        server.verify();
    }

    @Test
    @DisplayName("getOrderDetail mapeia campo isTest quando vem como 'test' (variação da API)")
    void getOrderDetail_shouldMapTestFieldAlias() {
        server.expect(requestTo(BASE_URL + "/orders/ord_2"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withSuccess("""
                      {"id":"ord_2","category":"FOOD","test":true,
                       "merchant":{"id":"m1","name":"Loja"},
                       "items":[],"total":{"orderAmount":0}}
                      """, MediaType.APPLICATION_JSON));

        IfoodOrderDetailResponse detail = client.getOrderDetail("access.jwt", "ord_2").body();

        assertThat(detail.isTest()).isTrue();
        server.verify();
    }
}
