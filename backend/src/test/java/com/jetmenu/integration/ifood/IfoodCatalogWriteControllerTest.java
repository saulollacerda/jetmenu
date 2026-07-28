package com.jetmenu.integration.ifood;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.integration.ifood.dto.IfoodCatalogBatchResponse;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult.ItemOutcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogPublishResult.Outcome;
import com.jetmenu.integration.ifood.dto.IfoodCatalogStatusChange;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult;
import com.jetmenu.integration.ifood.dto.IfoodCatalogSyncResult.SkippedProduct;
import com.jetmenu.integration.ifood.services.IfoodCatalogImportService;
import com.jetmenu.integration.ifood.services.IfoodCatalogPublishService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IfoodCatalogController.class)
@WithMockUser
@DisplayName("IfoodCatalogController — escrita")
class IfoodCatalogWriteControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private IfoodCatalogImportService importService;
    @MockitoBean private IfoodCatalogPublishService publishService;
    @MockitoBean private AuthHelper authHelper;

    private UUID merchantId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        productId = UUID.randomUUID();
        given(authHelper.getMerchantId(any())).willReturn(merchantId);
    }

    @Test
    @DisplayName("POST /publish retorna 200 com o resumo da publicação")
    void publish_shouldReturnSummary() throws Exception {
        UUID failedId = UUID.randomUUID();
        given(publishService.publish(eq(merchantId), anyList())).willReturn(
                IfoodCatalogPublishResult.of(List.of(
                        new ItemOutcome(productId, "X-Burger", "MB-1", Outcome.PUBLISHED, null),
                        new ItemOutcome(failedId, "Quebrado", "MB-2", Outcome.FAILED,
                                "Item ou categoria não encontrado no iFood."))));

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[\"" + productId + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedProducts").value(1))
                .andExpect(jsonPath("$.skippedProducts").value(1))
                .andExpect(jsonPath("$.items[0].productId").value(productId.toString()))
                .andExpect(jsonPath("$.items[0].name").value("X-Burger"))
                .andExpect(jsonPath("$.items[0].externalCode").value("MB-1"))
                .andExpect(jsonPath("$.items[0].outcome").value("PUBLISHED"))
                .andExpect(jsonPath("$.items[0].reason").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].outcome").value("FAILED"))
                .andExpect(jsonPath("$.items[1].reason").exists());
    }

    @Test
    @DisplayName("POST /publish sem corpo publica todos os produtos ativos")
    void publish_shouldAcceptMissingBody() throws Exception {
        given(publishService.publish(eq(merchantId), eq(null)))
                .willReturn(IfoodCatalogPublishResult.of(List.of()));

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publishedProducts").value(0));

        then(publishService).should().publish(merchantId, null);
    }

    @Test
    @DisplayName("PATCH /prices retorna 200 com batchId e ignorados")
    void syncPrices_shouldReturnBatch() throws Exception {
        UUID skippedId = UUID.randomUUID();
        given(publishService.syncPrices(eq(merchantId), anyList())).willReturn(
                new IfoodCatalogSyncResult("batch-1", 2, List.of(
                        new SkippedProduct(skippedId, "Produto ainda não foi publicado no iFood."))));

        mockMvc.perform(patch("/api/integrations/ifood/catalog/prices").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-1"))
                .andExpect(jsonPath("$.requested").value(2))
                .andExpect(jsonPath("$.skipped[0].productId").value(skippedId.toString()))
                .andExpect(jsonPath("$.skipped[0].reason").exists());
    }

    @Test
    @DisplayName("PATCH /status envia as mudanças pedidas e retorna o batchId")
    void syncStatus_shouldReturnBatch() throws Exception {
        given(publishService.syncStatus(eq(merchantId), anyList()))
                .willReturn(new IfoodCatalogSyncResult("batch-2", 1, List.of()));

        mockMvc.perform(patch("/api/integrations/ifood/catalog/status").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productId\":\"" + productId
                                + "\",\"status\":\"UNAVAILABLE\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-2"))
                .andExpect(jsonPath("$.requested").value(1))
                .andExpect(jsonPath("$.skipped").isArray());

        then(publishService).should().syncStatus(merchantId,
                List.of(new IfoodCatalogStatusChange(productId, "UNAVAILABLE")));
    }

    @Test
    @DisplayName("GET /batch/{batchId} devolve o resultado consolidado do lote")
    void getBatch_shouldReturnBatchResult() throws Exception {
        given(publishService.getBatch(merchantId, "batch-1")).willReturn(
                new IfoodCatalogBatchResponse("batch-1", "COMPLETED", 2, 1, List.of(
                        new IfoodCatalogBatchResponse.Result("prod-1", "SUCCESS"),
                        new IfoodCatalogBatchResponse.Result("prod-2", "ERROR"))));

        mockMvc.perform(get("/api/integrations/ifood/catalog/batch/batch-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value("batch-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failureCount").value(1))
                .andExpect(jsonPath("$.results[1].resourceId").value("prod-2"))
                .andExpect(jsonPath("$.results[1].result").value("ERROR"));
    }

    @Test
    @DisplayName("erro de validação do iFood vira 422 com mensagem em pt-BR")
    void publish_shouldReturn422OnValidationError() throws Exception {
        willThrow(new IfoodBadRequestException("externalCode obrigatório"))
                .given(publishService).publish(eq(merchantId), any());

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("recurso inexistente no iFood vira 404")
    void getBatch_shouldReturn404WhenBatchIsUnknown() throws Exception {
        willThrow(new IfoodResourceNotFoundException())
                .given(publishService).getBatch(merchantId, "batch-x");

        mockMvc.perform(get("/api/integrations/ifood/catalog/batch/batch-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("conflito no iFood vira 409")
    void publish_shouldReturn409OnConflict() throws Exception {
        willThrow(new IfoodCatalogConflictException("externalCode duplicado"))
                .given(publishService).publish(eq(merchantId), any());

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("indisponibilidade do iFood após os retries vira 503")
    void publish_shouldReturn503WhenIfoodIsUnavailable() throws Exception {
        willThrow(new IfoodUnavailableException(new RuntimeException("boom")))
                .given(publishService).publish(eq(merchantId), any());

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("merchant desconectado continua devolvendo 409")
    void publish_shouldReturn409WhenNotConnected() throws Exception {
        willThrow(new IllegalStateException("not connected"))
                .given(publishService).publish(eq(merchantId), any());

        mockMvc.perform(post("/api/integrations/ifood/catalog/publish").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }
}
