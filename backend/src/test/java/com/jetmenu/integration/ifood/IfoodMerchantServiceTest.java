package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodInterruptionRequest;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursRequest;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
import com.jetmenu.merchant.Merchant;
import com.jetmenu.merchant.MerchantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("IfoodMerchantService")
class IfoodMerchantServiceTest {

    private static final String IFOOD_MID = "ifood-m1";

    @Mock private IfoodMerchantClient merchantClient;
    @Mock private IfoodTokenService tokenService;
    @Mock private MerchantRepository merchantRepository;

    @InjectMocks
    private IfoodMerchantService service;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        Merchant merchant = Merchant.builder().id(merchantId).build();
        merchant.setIfoodMerchantId(IFOOD_MID);
        lenient().when(merchantRepository.findById(merchantId)).thenReturn(Optional.of(merchant));
        lenient().when(tokenService.getAccessToken()).thenReturn("t1");
    }

    private static HttpClientErrorException clientError(HttpStatus status, String body) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), new HttpHeaders(),
                body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private static HttpServerErrorException serverError() {
        return HttpServerErrorException.create(
                HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error",
                new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
    }

    // --- happy paths -----------------------------------------------------------------

    @Test
    @DisplayName("getDetails resolve o ifoodMerchantId e delega ao client")
    void getDetails_happyPath() {
        IfoodMerchantDetailsResponse details = new IfoodMerchantDetailsResponse(IFOOD_MID, "Loja", "Loja LTDA");
        given(merchantClient.getDetails("t1", IFOOD_MID)).willReturn(details);

        assertThat(service.getDetails(merchantId)).isSameAs(details);
    }

    @Test
    @DisplayName("getStatus delega ao client")
    void getStatus_happyPath() {
        List<IfoodMerchantStatusResponse> status = List.of(
                new IfoodMerchantStatusResponse("DELIVERY", "IFOOD", true, "OK", null, List.of()));
        given(merchantClient.getStatus("t1", IFOOD_MID)).willReturn(status);

        assertThat(service.getStatus(merchantId)).isEqualTo(status);
    }

    @Test
    @DisplayName("getInterruptions delega ao client")
    void getInterruptions_happyPath() {
        List<IfoodInterruptionResponse> list = List.of(
                new IfoodInterruptionResponse("int-1", "Almoço", "s", "e"));
        given(merchantClient.getInterruptions("t1", IFOOD_MID)).willReturn(list);

        assertThat(service.getInterruptions(merchantId)).isEqualTo(list);
    }

    @Test
    @DisplayName("createInterruption delega ao client")
    void createInterruption_happyPath() {
        IfoodInterruptionRequest request = new IfoodInterruptionRequest("Pausa", "s", "e");
        IfoodInterruptionResponse created = new IfoodInterruptionResponse("int-9", "Pausa", "s", "e");
        given(merchantClient.createInterruption("t1", IFOOD_MID, request)).willReturn(created);

        assertThat(service.createInterruption(merchantId, request)).isSameAs(created);
    }

    @Test
    @DisplayName("deleteInterruption delega ao client")
    void deleteInterruption_happyPath() {
        service.deleteInterruption(merchantId, "int-9");

        then(merchantClient).should().deleteInterruption("t1", IFOOD_MID, "int-9");
    }

    @Test
    @DisplayName("getOpeningHours delega ao client")
    void getOpeningHours_happyPath() {
        IfoodOpeningHoursResponse hours = new IfoodOpeningHoursResponse(List.of());
        given(merchantClient.getOpeningHours("t1", IFOOD_MID)).willReturn(hours);

        assertThat(service.getOpeningHours(merchantId)).isSameAs(hours);
    }

    @Test
    @DisplayName("updateOpeningHours delega ao client")
    void updateOpeningHours_happyPath() {
        IfoodOpeningHoursRequest request = new IfoodOpeningHoursRequest(List.of(
                new IfoodOpeningHoursRequest.Shift("MONDAY", "09:00:00", 480)));
        IfoodOpeningHoursResponse result = new IfoodOpeningHoursResponse(List.of());
        given(merchantClient.updateOpeningHours("t1", IFOOD_MID, request)).willReturn(result);

        assertThat(service.updateOpeningHours(merchantId, request)).isSameAs(result);
    }

    // --- not connected ---------------------------------------------------------------

    @Test
    @DisplayName("lança IllegalStateException quando o merchant não está conectado ao iFood")
    void notConnected_throwsIllegalState() {
        Merchant disconnected = Merchant.builder().id(merchantId).build();
        given(merchantRepository.findById(merchantId)).willReturn(Optional.of(disconnected));

        assertThatThrownBy(() -> service.getStatus(merchantId))
                .isInstanceOf(IllegalStateException.class);
        then(merchantClient).shouldHaveNoInteractions();
    }

    // --- 401 refresh + retry once ----------------------------------------------------

    @Test
    @DisplayName("401 do iFood força refresh do token e repete a chamada uma única vez")
    void unauthorized_refreshesTokenAndRetriesOnce() {
        List<IfoodMerchantStatusResponse> status = List.of();
        given(merchantClient.getStatus("t1", IFOOD_MID)).willThrow(clientError(HttpStatus.UNAUTHORIZED, null));
        given(tokenService.handleUnauthorized()).willReturn("t2");
        given(merchantClient.getStatus("t2", IFOOD_MID)).willReturn(status);

        assertThat(service.getStatus(merchantId)).isEqualTo(status);
        then(tokenService).should().handleUnauthorized();
        then(merchantClient).should().getStatus("t2", IFOOD_MID);
    }

    // --- transient 5xx retry ---------------------------------------------------------

    @Test
    @DisplayName("5xx é retentado com backoff e sucede na tentativa seguinte")
    void serverError_isRetriedThenSucceeds() {
        IfoodMerchantDetailsResponse details = new IfoodMerchantDetailsResponse(IFOOD_MID, "Loja", "Loja LTDA");
        given(merchantClient.getDetails("t1", IFOOD_MID))
                .willThrow(serverError())
                .willReturn(details);

        assertThat(service.getDetails(merchantId)).isSameAs(details);
        then(merchantClient).should(times(2)).getDetails("t1", IFOOD_MID);
    }

    @Test
    @DisplayName("erro de rede (ResourceAccessException) é retentado")
    void networkError_isRetriedThenSucceeds() {
        IfoodMerchantDetailsResponse details = new IfoodMerchantDetailsResponse(IFOOD_MID, "Loja", "Loja LTDA");
        given(merchantClient.getDetails("t1", IFOOD_MID))
                .willThrow(new ResourceAccessException("connection reset"))
                .willReturn(details);

        assertThat(service.getDetails(merchantId)).isSameAs(details);
        then(merchantClient).should(times(2)).getDetails("t1", IFOOD_MID);
    }

    @Test
    @DisplayName("5xx persistente esgota as tentativas e propaga o erro")
    void serverError_exhaustsAttempts() {
        given(merchantClient.getDetails("t1", IFOOD_MID)).willThrow(serverError());

        assertThatThrownBy(() -> service.getDetails(merchantId))
                .isInstanceOf(HttpServerErrorException.class);
        then(merchantClient).should(times(IfoodMerchantService.MAX_ATTEMPTS)).getDetails("t1", IFOOD_MID);
    }

    // --- 4xx never retried, translated ----------------------------------------------

    @Test
    @DisplayName("409 na criação de pausa vira IfoodInterruptionOverlapException e NÃO é retentado")
    void conflict_translatedToOverlapAndNotRetried() {
        IfoodInterruptionRequest request = new IfoodInterruptionRequest("Pausa", "s", "e");
        given(merchantClient.createInterruption("t1", IFOOD_MID, request))
                .willThrow(clientError(HttpStatus.CONFLICT, "{\"code\":\"InterruptionOverlap\"}"));

        assertThatThrownBy(() -> service.createInterruption(merchantId, request))
                .isInstanceOf(IfoodInterruptionOverlapException.class);
        then(merchantClient).should(times(1)).createInterruption("t1", IFOOD_MID, request);
    }

    @Test
    @DisplayName("400 genérico vira IfoodBadRequestException com o detalhe do iFood")
    void badRequest_translatedWithDetail() {
        IfoodInterruptionRequest request = new IfoodInterruptionRequest("Pausa", "s", "e");
        given(merchantClient.createInterruption("t1", IFOOD_MID, request))
                .willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"start must be before end\"}"));

        assertThatThrownBy(() -> service.createInterruption(merchantId, request))
                .isInstanceOf(IfoodBadRequestException.class)
                .satisfies(ex -> assertThat(((IfoodBadRequestException) ex).getDetail())
                        .isEqualTo("start must be before end"));
    }

    @Test
    @DisplayName("404 vira IfoodResourceNotFoundException")
    void notFound_translated() {
        willThrow(clientError(HttpStatus.NOT_FOUND, null))
                .given(merchantClient).deleteInterruption("t1", IFOOD_MID, "int-x");

        assertThatThrownBy(() -> service.deleteInterruption(merchantId, "int-x"))
                .isInstanceOf(IfoodResourceNotFoundException.class);
    }

    @Test
    @DisplayName("400 no update de horários vira IfoodShiftOverlapException (turnos sobrepostos)")
    void openingHours_badRequestTranslatedToShiftOverlap() {
        IfoodOpeningHoursRequest request = new IfoodOpeningHoursRequest(List.of(
                new IfoodOpeningHoursRequest.Shift("MONDAY", "09:00:00", 480)));
        given(merchantClient.updateOpeningHours("t1", IFOOD_MID, request))
                .willThrow(clientError(HttpStatus.BAD_REQUEST, "{\"message\":\"overlapping shifts\"}"));

        assertThatThrownBy(() -> service.updateOpeningHours(merchantId, request))
                .isInstanceOf(IfoodShiftOverlapException.class);
        then(merchantClient).should(times(1)).updateOpeningHours("t1", IFOOD_MID, request);
    }

    @Test
    @DisplayName("4xx não dispara chamadas de refresh de token nem novas tentativas")
    void clientError_doesNotRefreshOrRetry() {
        given(merchantClient.getStatus("t1", IFOOD_MID))
                .willThrow(clientError(HttpStatus.FORBIDDEN, null));

        assertThatThrownBy(() -> service.getStatus(merchantId))
                .isInstanceOf(HttpClientErrorException.class);
        then(tokenService).should(never()).handleUnauthorized();
        then(merchantClient).should(times(1)).getStatus(anyString(), any());
    }
}
