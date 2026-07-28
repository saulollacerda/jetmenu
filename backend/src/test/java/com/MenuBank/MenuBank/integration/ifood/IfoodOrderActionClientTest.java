package com.MenuBank.MenuBank.integration.ifood;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

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
}
