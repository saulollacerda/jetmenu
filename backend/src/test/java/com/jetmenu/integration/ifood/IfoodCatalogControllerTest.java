package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult.ItemOutcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogImportResult.Outcome;
import com.jetmenu.integration.ifood.services.IfoodCatalogImportService;
import com.jetmenu.integration.ifood.services.IfoodCatalogPublishService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodCatalogController.class)
@WithMockUser
@DisplayName("IfoodCatalogController")
class IfoodCatalogControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IfoodCatalogImportService importService;
    @MockitoBean private IfoodCatalogPublishService publishService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("POST /api/integrations/ifood/catalog/import retorna 200 com o resumo")
    void importCatalog_shouldReturnSummary() throws Exception {
        IfoodCatalogImportResult result = IfoodCatalogImportResult.of(List.of(
                new ItemOutcome("X-Burger", "BURGER_001", Outcome.IMPORTED, null),
                new ItemOutcome("Coca-Cola", "COKE_001", Outcome.LINKED, null),
                new ItemOutcome("Pizza", "PIZZA_01", Outcome.SKIPPED, "Item sem preço no catálogo")),
                2, 1);
        given(importService.importCatalog(merchantId)).willReturn(result);

        mockMvc.perform(post("/api/integrations/ifood/catalog/import").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.importedProducts").value(1))
                .andExpect(jsonPath("$.linkedProducts").value(1))
                .andExpect(jsonPath("$.skippedProducts").value(1))
                .andExpect(jsonPath("$.importedCategories").value(2))
                .andExpect(jsonPath("$.linkedCategories").value(1))
                .andExpect(jsonPath("$.items[0].name").value("X-Burger"))
                .andExpect(jsonPath("$.items[0].outcome").value("IMPORTED"))
                .andExpect(jsonPath("$.items[2].reason").value("Item sem preço no catálogo"));
    }

    @Test
    @DisplayName("retorna 409 quando o merchant não está conectado ao iFood")
    void importCatalog_shouldReturn409WhenNotConnected() throws Exception {
        willThrow(new IllegalStateException("not connected"))
                .given(importService).importCatalog(merchantId);

        mockMvc.perform(post("/api/integrations/ifood/catalog/import").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("retorna 409 quando o token expirou e é preciso reautorizar")
    void importCatalog_shouldReturn409WhenReauthorizationRequired() throws Exception {
        willThrow(new IfoodReauthorizationRequiredException())
                .given(importService).importCatalog(merchantId);

        mockMvc.perform(post("/api/integrations/ifood/catalog/import").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }
}
