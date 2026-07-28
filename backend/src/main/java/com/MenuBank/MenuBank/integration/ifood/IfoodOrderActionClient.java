package com.MenuBank.MenuBank.integration.ifood;

import com.MenuBank.MenuBank.integration.ifood.dto.IfoodCancellationReasonResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Write client for the iFood Order lifecycle actions required by homologation:
 * {@code confirm} (mandatory within the 8-minute SLA for DELIVERY/TAKEOUT),
 * {@code readyToPickup} (TAKEOUT — notifies the customer the order is ready),
 * {@code dispatch} (DELIVERY with the merchant's own delivery) and the cancellation
 * family (list reasons, request a cancellation, answer the customer's request).
 *
 * <p>Every action except {@code cancellationReasons} is fire-and-forget: iFood answers
 * {@code 202 Accepted} with no body.
 *
 * <p><strong>HTTP verbs and paths are NOT validated.</strong> Each action declares its verb
 * and path segment on a single line so a correction is a one-line change. {@code readyToPickup}
 * and {@code dispatch} use {@code PUT} as documented in
 * {@code .claude/docs/integrations/ifood/HOMOLOGATION.md}; {@code confirm} and the whole
 * cancellation family use {@code POST}, and the cancellation path segments
 * ({@code cancellationReasons}, {@code requestCancellation}, {@code acceptCancellation},
 * {@code denyCancellation}) plus the {@code requestCancellation} body shape
 * ({@code cancellationCode} + {@code reason}) were inferred, not read from the live iFood
 * reference documentation. All of it must be confirmed against the sandbox before homologation.
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

    /** Reasons iFood accepts for cancelling this specific order. */
    public List<IfoodCancellationReasonResponse> cancellationReasons(String accessToken, String ifoodOrderId) {
        List<IfoodCancellationReasonResponse> reasons = restClient.get()
                .uri("/orders/{orderId}/{action}", ifoodOrderId, "cancellationReasons")
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(new ParameterizedTypeReference<List<IfoodCancellationReasonResponse>>() {});
        // 204 No Content yields a null body — an order with no reason available is not an error.
        return reasons != null ? reasons : List.of();
    }

    /** Asks iFood to cancel the order using a code taken from {@link #cancellationReasons}. */
    public void requestCancellation(String accessToken, String ifoodOrderId,
                                    String cancellationCode, String reason) {
        // HashMap (not Map.of) because the reason is optional and Map.of rejects null values.
        Map<String, String> body = new HashMap<>();
        body.put("cancellationCode", cancellationCode);
        body.put("reason", reason);
        sendWithBody(HttpMethod.POST, accessToken, ifoodOrderId, "requestCancellation", body);
    }

    /** Accepts a cancellation requested by the customer or by the platform. */
    public void acceptCancellation(String accessToken, String ifoodOrderId) {
        send(HttpMethod.POST, accessToken, ifoodOrderId, "acceptCancellation");
    }

    /** Rejects a cancellation requested by the customer or by the platform. */
    public void denyCancellation(String accessToken, String ifoodOrderId) {
        send(HttpMethod.POST, accessToken, ifoodOrderId, "denyCancellation");
    }

    private void send(HttpMethod method, String accessToken, String ifoodOrderId, String action) {
        restClient.method(method)
                .uri("/orders/{orderId}/{action}", ifoodOrderId, action)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .toBodilessEntity();
    }

    private void sendWithBody(HttpMethod method, String accessToken, String ifoodOrderId,
                              String action, Object body) {
        restClient.method(method)
                .uri("/orders/{orderId}/{action}", ifoodOrderId, action)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}
