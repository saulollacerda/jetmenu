package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodRawResponse;
import com.jetmenu.integration.ifood.services.IfoodDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodDiagnosticsController.class)
@WithMockUser
@DisplayName("IfoodDiagnosticsController")
class IfoodDiagnosticsControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IfoodDiagnosticsService diagnosticsService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("GET /access informa que a tela está liberada para o merchant")
    void access_shouldReportEnabled() throws Exception {
        given(diagnosticsService.isEnabledFor(merchantId)).willReturn(true);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("GET /access informa que a tela está bloqueada sem erro")
    void access_shouldReportDisabled() throws Exception {
        given(diagnosticsService.isEnabledFor(merchantId)).willReturn(false);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("GET /catalogs devolve endpoint, status e corpo cru do iFood")
    void listCatalogs_shouldReturnRawResponse() throws Exception {
        given(diagnosticsService.listCatalogs(merchantId)).willReturn(new IfoodRawResponse(
                "https://merchant-api.ifood.com.br/catalog/v2.0/merchants/ifood-m1/catalogs",
                200,
                "[{\"catalogId\":\"cat-default\"}]"));

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value(
                        "https://merchant-api.ifood.com.br/catalog/v2.0/merchants/ifood-m1/catalogs"))
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.body").value("[{\"catalogId\":\"cat-default\"}]"));
    }

    @Test
    @DisplayName("GET /items repassa o catalogId informado")
    void listItems_shouldForwardCatalogId() throws Exception {
        given(diagnosticsService.listItems(merchantId, "cat-default")).willReturn(
                new IfoodRawResponse("https://ifood/categories?includeItems=true", 200, "[]"));

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/items")
                        .param("catalogId", "cat-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("GET /items sem catalogId deixa o serviço resolver o catálogo")
    void listItems_shouldAllowMissingCatalogId() throws Exception {
        given(diagnosticsService.listItems(merchantId, null)).willReturn(
                new IfoodRawResponse("https://ifood/categories?includeItems=true", 200, "[]"));

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endpoint").value("https://ifood/categories?includeItems=true"));
    }

    @Test
    @DisplayName("merchant fora da whitelist recebe 403 com mensagem em pt-BR")
    void listCatalogs_shouldReturn403WhenNotAllowed() throws Exception {
        willThrow(new IfoodDiagnosticsNotAllowedException())
                .given(diagnosticsService).listCatalogs(merchantId);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/catalogs"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "O diagnóstico do iFood não está liberado para esta loja."));
    }

    @Test
    @DisplayName("merchant sem iFood conectado recebe 409")
    void listCatalogs_shouldReturn409WhenNotConnected() throws Exception {
        willThrow(new IllegalStateException("not connected"))
                .given(diagnosticsService).listCatalogs(merchantId);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/catalogs"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Conecte sua conta do iFood antes de usar o diagnóstico."));
    }

    @Test
    @DisplayName("sem catálogo no iFood recebe 404")
    void listItems_shouldReturn404WhenNoCatalog() throws Exception {
        willThrow(new IfoodResourceNotFoundException())
                .given(diagnosticsService).listItems(merchantId, null);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/items"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(
                        "Nenhum catálogo encontrado no iFood para esta loja."));
    }

    @Test
    @DisplayName("iFood indisponível vira 503")
    void listCatalogs_shouldReturn503WhenIfoodUnavailable() throws Exception {
        willThrow(new IfoodUnavailableException(new RuntimeException("timeout")))
                .given(diagnosticsService).listCatalogs(merchantId);

        mockMvc.perform(get("/api/integrations/ifood/diagnostics/catalogs"))
                .andExpect(status().isServiceUnavailable());
    }
}
