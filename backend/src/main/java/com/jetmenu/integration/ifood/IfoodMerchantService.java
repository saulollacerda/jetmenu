package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodInterruptionRequest;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursRequest;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Pure passthrough to the iFood Merchant API — status, interruptions (pauses) and opening
 * hours are owned by iFood, so nothing is persisted locally. Each public method resolves
 * the linked {@code ifoodMerchantId}, obtains the app-level token and delegates to
 * {@link IfoodMerchantClient}.
 *
 * <p>Resilience required by iFood homologation:
 * <ul>
 *   <li>Exponential backoff retry for transient failures ONLY — {@code 5xx} and network
 *       errors ({@link ResourceAccessException}). Up to {@value #MAX_ATTEMPTS} attempts.</li>
 *   <li>{@code 4xx} is NEVER retried — it is translated once into a typed exception so a
 *       repeated {@code 409}/{@code 400} does not spam the API.</li>
 *   <li>A {@code 401} triggers a single token refresh via
 *       {@link IfoodTokenService#handleUnauthorized()} and one retry, outside the backoff
 *       count.</li>
 * </ul>
 */
@Service
public class IfoodMerchantService {

    private static final Logger log = LoggerFactory.getLogger(IfoodMerchantService.class);

    static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 200L;

    private final IfoodMerchantClient merchantClient;
    private final IfoodTokenService tokenService;
    private final MerchantRepository merchantRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IfoodMerchantService(IfoodMerchantClient merchantClient,
                                IfoodTokenService tokenService,
                                MerchantRepository merchantRepository) {
        this.merchantClient = merchantClient;
        this.tokenService = tokenService;
        this.merchantRepository = merchantRepository;
    }

    public IfoodMerchantDetailsResponse getDetails(UUID merchantId) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        return execute(token -> merchantClient.getDetails(token, ifoodMerchantId));
    }

    public List<IfoodMerchantStatusResponse> getStatus(UUID merchantId) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        return execute(token -> merchantClient.getStatus(token, ifoodMerchantId));
    }

    public List<IfoodInterruptionResponse> getInterruptions(UUID merchantId) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        return execute(token -> merchantClient.getInterruptions(token, ifoodMerchantId));
    }

    public IfoodInterruptionResponse createInterruption(UUID merchantId, IfoodInterruptionRequest request) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        return execute(token -> merchantClient.createInterruption(token, ifoodMerchantId, request));
    }

    public void deleteInterruption(UUID merchantId, String interruptionId) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        execute(token -> {
            merchantClient.deleteInterruption(token, ifoodMerchantId, interruptionId);
            return null;
        });
    }

    public IfoodOpeningHoursResponse getOpeningHours(UUID merchantId) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        return execute(token -> merchantClient.getOpeningHours(token, ifoodMerchantId));
    }

    public IfoodOpeningHoursResponse updateOpeningHours(UUID merchantId, IfoodOpeningHoursRequest request) {
        String ifoodMerchantId = resolveIfoodMerchantId(merchantId);
        // Overlapping shifts surface as a 400 on this operation specifically.
        return execute(token -> merchantClient.updateOpeningHours(token, ifoodMerchantId, request),
                this::openingHoursErrorMapper);
    }

    private String resolveIfoodMerchantId(UUID merchantId) {
        return merchantRepository.findById(merchantId)
                .map(Merchant::getIfoodMerchantId)
                .filter(id -> id != null && !id.isBlank())
                .orElseThrow(() -> new IllegalStateException(
                        "Merchant " + merchantId + " is not connected to iFood"));
    }

    private <T> T execute(Function<String, T> operation) {
        return execute(operation, this::defaultErrorMapper);
    }

    private <T> T execute(Function<String, T> operation,
                          Function<HttpClientErrorException, RuntimeException> errorMapper) {
        RuntimeException lastTransient = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callOnce(operation);
            } catch (HttpClientErrorException e) {
                // 4xx is a client-side condition — never retry, translate and fail fast.
                throw errorMapper.apply(e);
            } catch (HttpServerErrorException | ResourceAccessException e) {
                lastTransient = e;
                log.warn("[iFood] falha transitória na API do Merchant (tentativa {}/{}): {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    backoff(attempt);
                }
            }
        }
        throw lastTransient;
    }

    private <T> T callOnce(Function<String, T> operation) {
        try {
            return operation.apply(tokenService.getAccessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("[iFood] 401 recebido — forçando refresh do token e repetindo a chamada");
            return operation.apply(tokenService.handleUnauthorized());
        }
    }

    private RuntimeException defaultErrorMapper(HttpClientErrorException e) {
        return switch (e.getStatusCode().value()) {
            case 409 -> new IfoodInterruptionOverlapException();
            case 400 -> new IfoodBadRequestException(extractDetail(e));
            case 404 -> new IfoodResourceNotFoundException();
            default -> e;
        };
    }

    private RuntimeException openingHoursErrorMapper(HttpClientErrorException e) {
        if (e.getStatusCode().value() == 400) {
            return new IfoodShiftOverlapException();
        }
        return defaultErrorMapper(e);
    }

    private String extractDetail(HttpClientErrorException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return e.getStatusText();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            for (String field : List.of("message", "error", "detail")) {
                JsonNode node = root.path(field);
                if (node.isTextual() && !node.asText().isBlank()) {
                    return node.asText();
                }
            }
        } catch (Exception ignored) {
            // Body is not JSON — fall back to the raw payload below.
        }
        return body;
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(BASE_BACKOFF_MILLIS * (1L << (attempt - 1)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off iFood retry", e);
        }
    }
}
