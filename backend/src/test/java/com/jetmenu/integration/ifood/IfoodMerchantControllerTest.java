package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodInterruptionResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantDetailsResponse;
import com.jetmenu.integration.ifood.dto.IfoodMerchantStatusResponse;
import com.jetmenu.integration.ifood.dto.IfoodOpeningHoursResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodMerchantController.class)
@WithMockUser
@DisplayName("IfoodMerchantController")
class IfoodMerchantControllerTest {

    private static final String BASE = "/api/integrations/ifood/merchant";

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IfoodMerchantService merchantService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("GET /details retorna 200 com id, name e corporateName")
    void getDetails_returns200() throws Exception {
        given(merchantService.getDetails(merchantId))
                .willReturn(new IfoodMerchantDetailsResponse("ifood-m1", "Loja", "Loja LTDA"));

        mockMvc.perform(get(BASE + "/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("ifood-m1"))
                .andExpect(jsonPath("$.name").value("Loja"))
                .andExpect(jsonPath("$.corporateName").value("Loja LTDA"));
    }

    @Test
    @DisplayName("GET /status retorna 200 com o array de operações")
    void getStatus_returns200() throws Exception {
        given(merchantService.getStatus(merchantId)).willReturn(List.of(
                new IfoodMerchantStatusResponse("DELIVERY", "IFOOD", true, "OK", null,
                        List.of(new IfoodMerchantStatusResponse.Validation("v1", "is-connected", "OK", null)))));

        mockMvc.perform(get(BASE + "/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].operation").value("DELIVERY"))
                .andExpect(jsonPath("$[0].available").value(true))
                .andExpect(jsonPath("$[0].state").value("OK"))
                .andExpect(jsonPath("$[0].validations[0].code").value("is-connected"));
    }

    @Test
    @DisplayName("GET /interruptions retorna 200 com o array de pausas")
    void getInterruptions_returns200() throws Exception {
        given(merchantService.getInterruptions(merchantId)).willReturn(List.of(
                new IfoodInterruptionResponse("int-1", "Almoço", "2026-07-17T12:00:00Z", "2026-07-17T13:00:00Z")));

        mockMvc.perform(get(BASE + "/interruptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("int-1"))
                .andExpect(jsonPath("$[0].description").value("Almoço"));
    }

    @Test
    @DisplayName("POST /interruptions retorna 201 com a pausa criada")
    void createInterruption_returns201() throws Exception {
        given(merchantService.createInterruption(eq(merchantId), any())).willReturn(
                new IfoodInterruptionResponse("int-9", "Manutenção", "2026-07-17T15:00:00Z", "2026-07-17T16:00:00Z"));

        mockMvc.perform(post(BASE + "/interruptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Manutenção","start":"2026-07-17T15:00:00Z","end":"2026-07-17T16:00:00Z"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("int-9"))
                .andExpect(jsonPath("$.description").value("Manutenção"));
    }

    @Test
    @DisplayName("DELETE /interruptions/{id} retorna 204 sem conteúdo")
    void deleteInterruption_returns204() throws Exception {
        doNothing().when(merchantService).deleteInterruption(merchantId, "int-9");

        mockMvc.perform(delete(BASE + "/interruptions/int-9").with(csrf()))
                .andExpect(status().isNoContent());

        then(merchantService).should().deleteInterruption(merchantId, "int-9");
    }

    @Test
    @DisplayName("GET /opening-hours retorna 200 com os turnos")
    void getOpeningHours_returns200() throws Exception {
        given(merchantService.getOpeningHours(merchantId)).willReturn(new IfoodOpeningHoursResponse(List.of(
                new IfoodOpeningHoursResponse.Shift("s1", "MONDAY", "09:00:00", 480))));

        mockMvc.perform(get(BASE + "/opening-hours"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shifts[0].dayOfWeek").value("MONDAY"))
                .andExpect(jsonPath("$.shifts[0].start").value("09:00:00"))
                .andExpect(jsonPath("$.shifts[0].duration").value(480));
    }

    @Test
    @DisplayName("PUT /opening-hours retorna 200 com os turnos resultantes")
    void updateOpeningHours_returns200() throws Exception {
        given(merchantService.updateOpeningHours(eq(merchantId), any())).willReturn(new IfoodOpeningHoursResponse(List.of(
                new IfoodOpeningHoursResponse.Shift("s2", "TUESDAY", "10:00:00", 300))));

        mockMvc.perform(put(BASE + "/opening-hours").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shifts":[{"dayOfWeek":"TUESDAY","start":"10:00:00","duration":300}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shifts[0].dayOfWeek").value("TUESDAY"))
                .andExpect(jsonPath("$.shifts[0].duration").value(300));
    }

    // --- ProblemDetail mappings ------------------------------------------------------

    @Test
    @DisplayName("retorna 409 com detalhe pt-BR quando o merchant não está conectado")
    void notConnected_returns409() throws Exception {
        given(merchantService.getStatus(merchantId))
                .willThrow(new IllegalStateException("not connected"));

        mockMvc.perform(get(BASE + "/status"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Conecte sua conta do iFood para gerenciar sua loja."));
    }

    @Test
    @DisplayName("retorna 409 com mensagem de sobreposição quando a pausa conflita")
    void interruptionOverlap_returns409() throws Exception {
        given(merchantService.createInterruption(eq(merchantId), any()))
                .willThrow(new IfoodInterruptionOverlapException());

        mockMvc.perform(post(BASE + "/interruptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Pausa","start":"2026-07-17T15:00:00Z","end":"2026-07-17T16:00:00Z"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Já existe uma pausa nesse período. Remova a pausa existente ou escolha outro horário."));
    }

    @Test
    @DisplayName("retorna 400 com detalhe do iFood para dados inválidos")
    void badRequest_returns400() throws Exception {
        given(merchantService.createInterruption(eq(merchantId), any()))
                .willThrow(new IfoodBadRequestException("start must be before end"));

        mockMvc.perform(post(BASE + "/interruptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"description":"Pausa","start":"2026-07-17T15:00:00Z","end":"2026-07-17T16:00:00Z"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Dados inválidos: start must be before end"));
    }

    @Test
    @DisplayName("retorna 400 com mensagem de turnos sobrepostos no update de horários")
    void shiftOverlap_returns400() throws Exception {
        given(merchantService.updateOpeningHours(eq(merchantId), any()))
                .willThrow(new IfoodShiftOverlapException());

        mockMvc.perform(put(BASE + "/opening-hours").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"shifts":[{"dayOfWeek":"MONDAY","start":"09:00:00","duration":480}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Os turnos informados se sobrepõem."));
    }

    @Test
    @DisplayName("retorna 404 quando o recurso não existe no iFood")
    void notFound_returns404() throws Exception {
        doThrow(new IfoodResourceNotFoundException())
                .when(merchantService).deleteInterruption(merchantId, "int-x");

        mockMvc.perform(delete(BASE + "/interruptions/int-x").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Recurso não encontrado no iFood."));
    }

    @Test
    @DisplayName("retorna 409 quando a autorização com o iFood expirou")
    void reauthorizationRequired_returns409() throws Exception {
        given(merchantService.getStatus(merchantId))
                .willThrow(new IfoodReauthorizationRequiredException());

        mockMvc.perform(get(BASE + "/status"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }
}
