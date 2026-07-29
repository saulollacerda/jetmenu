package com.jetmenu.integration.ifood;

import com.jetmenu.integration.ifood.dto.IfoodInterruptionRequest;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursRequest;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("IfoodMerchantClient")
class IfoodMerchantClientTest {

    private static final String BASE_URL = "https://merchant-api.ifood.com.br/merchant/v1.0";
    private static final String MID = "ifood-m1";

    private MockRestServiceServer server;
    private IfoodMerchantClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new IfoodMerchantClient(builder, BASE_URL);
    }

    @Test
    @DisplayName("getDetails envia Bearer token e mapeia id/name/corporateName")
    void getDetails_shouldReturnMappedDetails() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withSuccess("""
                        {"id":"ifood-m1","name":"Loja Exemplo","corporateName":"Exemplo LTDA",
                         "address":{"city":"São Paulo"}}
                        """, MediaType.APPLICATION_JSON));

        IfoodMerchantDetailsResponse details = client.getDetails("tok", MID);

        assertThat(details.id()).isEqualTo("ifood-m1");
        assertThat(details.name()).isEqualTo("Loja Exemplo");
        assertThat(details.corporateName()).isEqualTo("Exemplo LTDA");
        server.verify();
    }

    @Test
    @DisplayName("getStatus retorna a lista de operações com state, message e validations")
    void getStatus_shouldReturnStatusList() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"operation":"DELIVERY","salesChannel":"IFOOD","available":true,"state":"OK",
                           "message":null,
                           "validations":[
                             {"id":"v1","code":"is-connected","state":"OK","message":null}
                           ]},
                          {"operation":"DELIVERY","salesChannel":"IFOOD","available":false,"state":"CLOSED",
                           "message":{"title":"Loja fechada","subtitle":"Fora do horário"},
                           "validations":[]}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<IfoodMerchantStatusResponse> status = client.getStatus("tok", MID);

        assertThat(status).hasSize(2);
        assertThat(status.get(0).state()).isEqualTo("OK");
        assertThat(status.get(0).available()).isTrue();
        assertThat(status.get(0).validations()).hasSize(1);
        assertThat(status.get(0).validations().get(0).code()).isEqualTo("is-connected");
        assertThat(status.get(1).state()).isEqualTo("CLOSED");
        assertThat(status.get(1).message().title()).isEqualTo("Loja fechada");
        server.verify();
    }

    @Test
    @DisplayName("getStatus normaliza validations ausente para lista vazia (nunca null ao frontend)")
    void getStatus_shouldNormalizeMissingValidationsToEmptyList() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/status"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [
                          {"operation":"DELIVERY","salesChannel":"IFOOD","available":true,"state":"OK",
                           "message":null}
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<IfoodMerchantStatusResponse> status = client.getStatus("tok", MID);

        assertThat(status).hasSize(1);
        assertThat(status.get(0).validations()).isNotNull().isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("getInterruptions retorna a lista de pausas")
    void getInterruptions_shouldReturnList() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/interruptions"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{"id":"int-1","description":"Almoço","start":"2026-07-17T12:00:00Z",
                          "end":"2026-07-17T13:00:00Z"}]
                        """, MediaType.APPLICATION_JSON));

        List<IfoodInterruptionResponse> interruptions = client.getInterruptions("tok", MID);

        assertThat(interruptions).hasSize(1);
        assertThat(interruptions.get(0).id()).isEqualTo("int-1");
        assertThat(interruptions.get(0).description()).isEqualTo("Almoço");
        server.verify();
    }

    @Test
    @DisplayName("createInterruption faz POST com o corpo e retorna a pausa criada")
    void createInterruption_shouldPostAndReturnCreated() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/interruptions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer tok"))
                .andExpect(jsonPath("$.description").value("Manutenção"))
                .andExpect(jsonPath("$.start").value("2026-07-17T15:00:00Z"))
                .andRespond(withStatus(HttpStatus.CREATED).body("""
                        {"id":"int-9","description":"Manutenção","start":"2026-07-17T15:00:00Z",
                         "end":"2026-07-17T16:00:00Z"}
                        """).contentType(MediaType.APPLICATION_JSON));

        IfoodInterruptionResponse created = client.createInterruption("tok", MID,
                new IfoodInterruptionRequest("Manutenção", "2026-07-17T15:00:00Z", "2026-07-17T16:00:00Z"));

        assertThat(created.id()).isEqualTo("int-9");
        server.verify();
    }

    @Test
    @DisplayName("deleteInterruption faz DELETE no id informado")
    void deleteInterruption_shouldDelete() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/interruptions/int-9"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer tok"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        client.deleteInterruption("tok", MID, "int-9");

        server.verify();
    }

    @Test
    @DisplayName("getOpeningHours retorna os turnos configurados")
    void getOpeningHours_shouldReturnShifts() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/opening-hours"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"shifts":[
                          {"id":"s1","dayOfWeek":"MONDAY","start":"09:00:00","duration":480}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        IfoodOpeningHoursResponse hours = client.getOpeningHours("tok", MID);

        assertThat(hours.shifts()).hasSize(1);
        assertThat(hours.shifts().get(0).dayOfWeek()).isEqualTo("MONDAY");
        assertThat(hours.shifts().get(0).duration()).isEqualTo(480);
        server.verify();
    }

    @Test
    @DisplayName("updateOpeningHours faz PUT com os turnos e retorna o resultado")
    void updateOpeningHours_shouldPutAndReturnShifts() {
        server.expect(requestTo(BASE_URL + "/merchants/" + MID + "/opening-hours"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.shifts[0].dayOfWeek").value("TUESDAY"))
                .andRespond(withSuccess("""
                        {"shifts":[
                          {"id":"s2","dayOfWeek":"TUESDAY","start":"10:00:00","duration":300}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        IfoodOpeningHoursResponse result = client.updateOpeningHours("tok", MID,
                new IfoodOpeningHoursRequest(List.of(
                        new IfoodOpeningHoursRequest.Shift("TUESDAY", "10:00:00", 300))));

        assertThat(result.shifts()).hasSize(1);
        assertThat(result.shifts().get(0).id()).isEqualTo("s2");
        server.verify();
    }
}
