package com.MenuBank.MenuBank.integration.ifood;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Write client for the iFood Order lifecycle actions required by homologation:
 * {@code confirm} (mandatory within the 8-minute SLA for DELIVERY/TAKEOUT),
 * {@code readyToPickup} (TAKEOUT — notifies the customer the order is ready) and
 * {@code dispatch} (DELIVERY with the merchant's own delivery).
 *
 * <p>All three are fire-and-forget: iFood answers {@code 202 Accepted} with no body.
 *
 * <p><strong>HTTP verbs.</strong> Each action declares its verb on a single line so a
 * correction is a one-line change. {@code readyToPickup} and {@code dispatch} use
 * {@code PUT} as documented in {@code .claude/docs/integrations/ifood/HOMOLOGATION.md};
 * {@code confirm} uses {@code POST}. This was NOT validated against the live iFood
 * reference documentation — it must be confirmed against the sandbox before homologation.
 */
@Component
public class IfoodOrderActionClient {

    private final RestClient restClient;

    public IfoodOrderActionClient(
            RestClient.Builder builder,
            @Value("${ifood.order-base-url}") String orderBaseUrl) {
        this.restClient = builder.baseUrl(orderBaseUrl).build();
    }

    public void confirm(String accessToken, String ifoodOrderId) {
        send(HttpMethod.POST, accessToken, ifoodOrderId, "confirm");
    }

    public void readyToPickup(String accessToken, String ifoodOrderId) {
        send(HttpMethod.PUT, accessToken, ifoodOrderId, "readyToPickup");
    }

    public void dispatch(String accessToken, String ifoodOrderId) {
        send(HttpMethod.PUT, accessToken, ifoodOrderId, "dispatch");
    }

    private void send(HttpMethod method, String accessToken, String ifoodOrderId, String action) {
        restClient.method(method)
                .uri("/orders/{orderId}/{action}", ifoodOrderId, action)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }
}
