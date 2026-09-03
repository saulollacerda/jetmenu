package com.jetmenu.internal;

import com.jetmenu.integration.anotaai.AnotaAIReconciliationResult;
import com.jetmenu.integration.anotaai.AnotaAIReconciliationService;
import com.jetmenu.integration.rawpayload.ExternalOrderRawPayloadService;
import com.jetmenu.integration.stripe.StripeCatalogSync;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// O autorizador entra de verdade, não mockado: é ele que decide se a rota responde, e um
// mock aqui testaria só o roteamento.
@WebMvcTest(InternalJobsController.class)
@Import(InternalJobAuthorizer.class)
@WithMockUser
@TestPropertySource(properties = "app.internal-jobs.token=token-de-teste")
@DisplayName("InternalJobsController")
class InternalJobsControllerTest {

    private static final String RECONCILE = "/api/internal/jobs/anotaai-reconcile";
    private static final String CLEANUP = "/api/internal/jobs/raw-payload-cleanup";
    private static final String STRIPE_CATALOG = "/api/internal/jobs/stripe-catalog-sync";
    private static final String HEADER = "X-Internal-Job-Token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnotaAIReconciliationService reconciliationService;

    @MockitoBean
    private ExternalOrderRawPayloadService rawPayloadService;

    @MockitoBean
    private StripeCatalogSync catalogSync;

    private AnotaAIReconciliationResult resultOf(int imported) {
        return AnotaAIReconciliationResult.builder()
                .merchantsScanned(3)
                .ordersImported(imported)
                .ordersSkipped(12)
                .errors(List.of())
                .build();
    }

    @Test
    @DisplayName("com o token correto roda a reconciliação e devolve o resumo")
    void shouldRunReconciliation() throws Exception {
        given(reconciliationService.reconcileAll()).willReturn(resultOf(0));

        mockMvc.perform(post(RECONCILE).with(csrf()).header(HEADER, "token-de-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantsScanned").value(3))
                .andExpect(jsonPath("$.ordersImported").value(0))
                .andExpect(jsonPath("$.ordersSkipped").value(12));
    }

    @Test
    @DisplayName("sem o token responde 404 e não roda nada")
    void shouldRejectMissingToken() throws Exception {
        mockMvc.perform(post(RECONCILE).with(csrf()))
                .andExpect(status().isNotFound());

        then(reconciliationService).should(never()).reconcileAll();
    }

    @Test
    @DisplayName("com token errado responde 404 e não roda nada")
    void shouldRejectWrongToken() throws Exception {
        mockMvc.perform(post(RECONCILE).with(csrf()).header(HEADER, "token-errado"))
                .andExpect(status().isNotFound());

        then(reconciliationService).should(never()).reconcileAll();
    }

    @Test
    @DisplayName("limpeza de payloads roda e devolve quantos foram removidos")
    void shouldRunRawPayloadCleanup() throws Exception {
        given(rawPayloadService.purgeExpired()).willReturn(42L);

        mockMvc.perform(post(CLEANUP).with(csrf()).header(HEADER, "token-de-teste"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(42));
    }

    @Test
    @DisplayName("limpeza sem token responde 404")
    void shouldRejectCleanupWithoutToken() throws Exception {
        mockMvc.perform(post(CLEANUP).with(csrf()))
                .andExpect(status().isNotFound());

        then(rawPayloadService).should(never()).purgeExpired();
    }

    /**
     * O catálogo da Stripe saiu do boot — era uma chamada de rede externa em cada instância
     * nova — e virou este job diário.
     */
    @Test
    @DisplayName("sincroniza o catálogo da Stripe fora do boot")
    void shouldRunStripeCatalogSync() throws Exception {
        mockMvc.perform(post(STRIPE_CATALOG).with(csrf()).header(HEADER, "token-de-teste"))
                .andExpect(status().isOk());

        then(catalogSync).should().sync();
    }

    @Test
    @DisplayName("catálogo da Stripe sem token responde 404")
    void shouldRejectStripeCatalogSyncWithoutToken() throws Exception {
        mockMvc.perform(post(STRIPE_CATALOG).with(csrf()))
                .andExpect(status().isNotFound());

        then(catalogSync).should(never()).sync();
    }
}
