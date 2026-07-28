package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.integration.ifood.dto.IfoodCancellationReasonResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodOrderActionClient")
class IfoodOrderActionClientTest {

    private static final String BASE_URL = "https://merchant-api.ifood.com.br/order/v1.0";

    private MockRestServiceServer server;
    private IfoodOrderActionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodOrderActionClient(builder, BASE_URL);
    }

    @Test
    @DisplayName("confirm envia POST /orders/{id}/confirm com Bearer token")
    void confirm_shouldPostConfirmWithBearerToken() {
        server.expect(requestTo(BASE_URL + "/orders/ord_1/confirm"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.confirm("access.jwt", "ord_1");

        server.verify();
    }

    @Test
    @DisplayName("readyToPickup envia PUT /orders/{id}/readyToPickup com Bearer token")
    void readyToPickup_shouldPutReadyToPickupWithBearerToken() {
        server.expect(requestTo(BASE_URL + "/orders/ord_2/readyToPickup"))
              .andExpect(method(HttpMethod.PUT))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.readyToPickup("access.jwt", "ord_2");

        server.verify();
    }

    @Test
    @DisplayName("dispatch envia PUT /orders/{id}/dispatch com Bearer token")
    void dispatch_shouldPutDispatchWithBearerToken() {
        server.expect(requestTo(BASE_URL + "/orders/ord_3/dispatch"))
              .andExpect(method(HttpMethod.PUT))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.dispatch("access.jwt", "ord_3");

        server.verify();
    }

    @Test
    @DisplayName("cancellationReasons faz GET /orders/{id}/cancellationReasons e mapeia código e descrição")
    void cancellationReasons_shouldGetAndMapCodeAndDescription() {
        server.expect(requestTo(BASE_URL + "/orders/ord_4/cancellationReasons"))
              .andExpect(method(HttpMethod.GET))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withSuccess("""
                      [
                        {"cancelCodeId":"501","description":"PROBLEMAS DE SISTEMA"},
                        {"cancelCodeId":"506","description":"ITEM INDISPONÍVEL"}
                      ]
                      """, MediaType.APPLICATION_JSON));

        List<IfoodCancellationReasonResponse> reasons = client.cancellationReasons("access.jwt", "ord_4");

        server.verify();
        assertThat(reasons).hasSize(2);
        assertThat(reasons.get(0).cancelCodeId()).isEqualTo("501");
        assertThat(reasons.get(0).description()).isEqualTo("PROBLEMAS DE SISTEMA");
        assertThat(reasons.get(1).cancelCodeId()).isEqualTo("506");
    }

    @Test
    @DisplayName("cancellationReasons devolve lista vazia quando o iFood responde sem corpo")
    void cancellationReasons_shouldReturnEmptyListWhenBodyIsAbsent() {
        server.expect(requestTo(BASE_URL + "/orders/ord_4/cancellationReasons"))
              .andExpect(method(HttpMethod.GET))
              .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(client.cancellationReasons("access.jwt", "ord_4")).isEmpty();

        server.verify();
    }

    @Test
    @DisplayName("requestCancellation envia POST /orders/{id}/requestCancellation com código e motivo")
    void requestCancellation_shouldPostCodeAndReason() {
        server.expect(requestTo(BASE_URL + "/orders/ord_5/requestCancellation"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andExpect(content().json("""
                      {"cancellationCode":"501","reason":"PROBLEMAS DE SISTEMA"}
                      """))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.requestCancellation("access.jwt", "ord_5", "501", "PROBLEMAS DE SISTEMA");

        server.verify();
    }

    @Test
    @DisplayName("acceptCancellation envia POST /orders/{id}/acceptCancellation com Bearer token")
    void acceptCancellation_shouldPostAcceptCancellation() {
        server.expect(requestTo(BASE_URL + "/orders/ord_6/acceptCancellation"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.acceptCancellation("access.jwt", "ord_6");

        server.verify();
    }

    @Test
    @DisplayName("denyCancellation envia POST /orders/{id}/denyCancellation com Bearer token")
    void denyCancellation_shouldPostDenyCancellation() {
        server.expect(requestTo(BASE_URL + "/orders/ord_7/denyCancellation"))
              .andExpect(method(HttpMethod.POST))
              .andExpect(header("Authorization", "Bearer access.jwt"))
              .andRespond(withStatus(HttpStatus.ACCEPTED));

        client.denyCancellation("access.jwt", "ord_7");

        server.verify();
    }
}
