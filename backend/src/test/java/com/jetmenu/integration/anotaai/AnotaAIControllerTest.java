package com.jetmenu.integration.anotaai;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnotaAIController.class)
@WithMockUser
@DisplayName("AnotaAIController")
class AnotaAIControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnotaAISyncService syncService;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);
    }

    @Test
    @DisplayName("POST /api/integrations/anotaai/orders deve retornar 200 com resultado")
    void shouldSyncOrders() throws Exception {
        given(syncService.syncOrders(any())).willReturn(AnotaAISyncResult.builder()
                .ordersImported(2)
                .ordersSkipped(1)
                .errors(List.of())
                .build());

        mockMvc.perform(post("/api/integrations/anotaai/orders").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordersImported").value(2))
                .andExpect(jsonPath("$.ordersSkipped").value(1));
    }

    @Test
    @DisplayName("POST /api/integrations/anotaai/catalog deve retornar 200 com resultado")
    void shouldSyncCatalog() throws Exception {
        given(syncService.syncCatalog(any(), org.mockito.ArgumentMatchers.anyBoolean()))
                .willReturn(AnotaAISyncResult.builder()
                .categoriesCreated(3)
                .categoriesUpdated(1)
                .productsCreated(10)
                .productsUpdated(2)
                .errors(List.of())
                .build());

        mockMvc.perform(post("/api/integrations/anotaai/catalog").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriesCreated").value(3))
                .andExpect(jsonPath("$.categoriesUpdated").value(1))
                .andExpect(jsonPath("$.productsCreated").value(10))
                .andExpect(jsonPath("$.productsUpdated").value(2));
    }
}
