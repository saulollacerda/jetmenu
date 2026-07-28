package com.jetmenu.integration.ifood.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing of the fields required by the iFood Order module homologation: card brand,
 * cash change, coupon value and who sponsors it, observations and pickup code.
 */
@DisplayName("IfoodOrderDetailResponse")
class IfoodOrderDetailResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String FULL_PAYLOAD = """
            {
              "id": "ord-1",
              "displayId": "3421",
              "orderType": "DELIVERY",
              "orderTiming": "IMMEDIATE",
              "category": "FOOD",
              "createdAt": "2026-07-01T18:00:00Z",
              "customer": {
                "id": "cus-1",
                "name": "Maria Santos",
                "documentNumber": "12345678909"
              },
              "items": [
                {
                  "name": "Açaí 500 ml",
                  "quantity": 1,
                  "unitPrice": 21.99,
                  "observations": "sem granola"
                }
              ],
              "payments": {
                "prepaid": 10.00,
                "pending": 39.97,
                "methods": [
                  {
                    "value": 39.97,
                    "currency": "BRL",
                    "method": "CASH",
                    "type": "OFFLINE",
                    "cash": { "changeFor": 50.00 }
                  },
                  {
                    "value": 10.00,
                    "currency": "BRL",
                    "method": "CREDIT",
                    "type": "ONLINE",
                    "card": { "brand": "VISA" }
                  }
                ]
              },
              "benefits": [
                {
                  "value": 12.00,
                  "target": "CART",
                  "sponsorshipValues": [
                    { "name": "IFOOD", "value": 8.00 },
                    { "name": "MERCHANT", "value": 4.00 }
                  ]
                }
              ],
              "delivery": {
                "mode": "DEFAULT",
                "deliveredBy": "MERCHANT",
                "deliveryDateTime": "2026-07-01T18:40:00Z",
                "observations": "Portão azul, tocar a campainha",
                "pickupCode": "9182",
                "unknownField": "ignored"
              },
              "takeout": {
                "mode": "DEFAULT",
                "takeoutDateTime": "2026-07-01T18:30:00Z"
              },
              "dineIn": {
                "startDateTime": "2026-07-01T18:10:00Z",
                "table": "12"
              }
            }
            """;

    @Test
    @DisplayName("deve parsear payments com bandeira do cartão e troco")
    void shouldParsePayments() throws Exception {
        IfoodOrderDetailResponse detail = mapper.readValue(FULL_PAYLOAD, IfoodOrderDetailResponse.class);

        IfoodOrderDetailResponse.Payments payments = detail.getPayments();
        assertThat(payments).isNotNull();
        assertThat(payments.getPrepaid()).isEqualByComparingTo("10.00");
        assertThat(payments.getPending()).isEqualByComparingTo("39.97");
        assertThat(payments.getMethods()).hasSize(2);

        IfoodOrderDetailResponse.PaymentMethod cash = payments.getMethods().get(0);
        assertThat(cash.getMethod()).isEqualTo("CASH");
        assertThat(cash.getType()).isEqualTo("OFFLINE");
        assertThat(cash.getCurrency()).isEqualTo("BRL");
        assertThat(cash.getValue()).isEqualByComparingTo("39.97");
        assertThat(cash.getCash().getChangeFor()).isEqualByComparingTo("50.00");
        assertThat(cash.getCard()).isNull();

        IfoodOrderDetailResponse.PaymentMethod credit = payments.getMethods().get(1);
        assertThat(credit.getCard().getBrand()).isEqualTo("VISA");
        assertThat(credit.getCash()).isNull();
    }

    @Test
    @DisplayName("deve parsear benefits com o rateio por patrocinador")
    void shouldParseBenefits() throws Exception {
        IfoodOrderDetailResponse detail = mapper.readValue(FULL_PAYLOAD, IfoodOrderDetailResponse.class);

        assertThat(detail.getBenefits()).hasSize(1);
        IfoodOrderDetailResponse.Benefit benefit = detail.getBenefits().get(0);
        assertThat(benefit.getValue()).isEqualByComparingTo("12.00");
        assertThat(benefit.getTarget()).isEqualTo("CART");
        assertThat(benefit.getSponsorshipValues())
                .extracting(IfoodOrderDetailResponse.SponsorshipValue::getName)
                .containsExactly("IFOOD", "MERCHANT");
        assertThat(benefit.getSponsorshipValues().get(0).getValue()).isEqualByComparingTo("8.00");
        assertThat(benefit.getSponsorshipValues().get(1).getValue()).isEqualByComparingTo("4.00");
    }

    @Test
    @DisplayName("deve parsear delivery, takeout e dineIn ignorando campos desconhecidos")
    void shouldParseDeliveryTakeoutAndDineIn() throws Exception {
        IfoodOrderDetailResponse detail = mapper.readValue(FULL_PAYLOAD, IfoodOrderDetailResponse.class);

        IfoodOrderDetailResponse.Delivery delivery = detail.getDelivery();
        assertThat(delivery.getMode()).isEqualTo("DEFAULT");
        assertThat(delivery.getDeliveredBy()).isEqualTo("MERCHANT");
        assertThat(delivery.getDeliveryDateTime()).isEqualTo("2026-07-01T18:40:00Z");
        assertThat(delivery.getObservations()).isEqualTo("Portão azul, tocar a campainha");
        assertThat(delivery.getPickupCode()).isEqualTo("9182");

        assertThat(detail.getTakeout().getMode()).isEqualTo("DEFAULT");
        assertThat(detail.getTakeout().getTakeoutDateTime()).isEqualTo("2026-07-01T18:30:00Z");

        assertThat(detail.getDineIn().getStartDateTime()).isEqualTo("2026-07-01T18:10:00Z");
        assertThat(detail.getDineIn().getTable()).isEqualTo("12");
    }

    @Test
    @DisplayName("deve parsear displayId, orderType/orderTiming, documento do cliente e observação do item")
    void shouldParseOrderIdentityAndObservations() throws Exception {
        IfoodOrderDetailResponse detail = mapper.readValue(FULL_PAYLOAD, IfoodOrderDetailResponse.class);

        assertThat(detail.getDisplayId()).isEqualTo("3421");
        assertThat(detail.getOrderType()).isEqualTo("DELIVERY");
        assertThat(detail.getOrderTiming()).isEqualTo("IMMEDIATE");
        assertThat(detail.getCustomer().getDocumentNumber()).isEqualTo("12345678909");
        assertThat(detail.getItems().get(0).getObservations()).isEqualTo("sem granola");
        assertThat(detail.getItems().get(0).getUnitPrice()).isEqualByComparingTo(new BigDecimal("21.99"));
    }

    @Test
    @DisplayName("deve tolerar payload sem os blocos opcionais")
    void shouldTolerateMissingBlocks() throws Exception {
        IfoodOrderDetailResponse detail = mapper.readValue(
                "{\"id\":\"ord-2\",\"category\":\"FOOD\"}", IfoodOrderDetailResponse.class);

        assertThat(detail.getPayments()).isNull();
        assertThat(detail.getBenefits()).isNull();
        assertThat(detail.getDelivery()).isNull();
        assertThat(detail.getTakeout()).isNull();
        assertThat(detail.getDineIn()).isNull();
    }
}
