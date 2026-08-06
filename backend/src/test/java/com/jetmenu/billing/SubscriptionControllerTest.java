package com.jetmenu.billing;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@WithMockUser
@DisplayName("SubscriptionController")
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscriptionService subscriptionService;

    @MockitoBean
    private BillingProvider billingProvider;

    @MockitoBean
    private AuthHelper authHelper;

    private UUID merchantId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(merchantId);
    }

    // -------------------------------------------------------------------------
    // GET /api/subscription/me
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/subscription/me")
    class GetMySubscription {

        @Test
        @DisplayName("deve retornar 200 com SubscriptionResponse")
        void shouldReturn200WithSubscriptionResponse() throws Exception {
            SubscriptionResponse response = SubscriptionResponse.builder()
                    .id(UUID.randomUUID())
                    .merchantId(merchantId)
                    .status(SubscriptionStatus.TRIAL)
                    .trialEndsAt(LocalDateTime.now().plusDays(5))
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            given(subscriptionService.getMySubscription(any())).willReturn(response);

            mockMvc.perform(get("/api/subscription/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("TRIAL"));
        }

        @Test
        @DisplayName("deve retornar 404 quando subscription não encontrada")
        void shouldReturn404WhenNotFound() throws Exception {
            given(subscriptionService.getMySubscription(any()))
                    .willThrow(new SubscriptionNotFoundException(merchantId));

            mockMvc.perform(get("/api/subscription/me"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/subscription/checkout
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/subscription/checkout")
    class CreateCheckout {

        @Test
        @DisplayName("deve retornar 200 com a URL devolvida pelo BillingProvider configurado")
        void shouldReturn200WithCheckoutUrl() throws Exception {
            UUID planId = UUID.randomUUID();
            given(billingProvider.createCheckout(any(), eq(planId)))
                    .willReturn(CheckoutResponse.builder()
                            .url("https://checkout.example/pay_123")
                            .build());

            mockMvc.perform(post("/api/subscription/checkout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planId + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").value("https://checkout.example/pay_123"));
        }

        @Test
        @DisplayName("deve retornar 400 quando planId está ausente")
        void shouldReturn400WhenPlanIdMissing() throws Exception {
            mockMvc.perform(post("/api/subscription/checkout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 404 quando o plano não existe")
        void shouldReturn404WhenPlanNotFound() throws Exception {
            UUID planId = UUID.randomUUID();
            given(billingProvider.createCheckout(any(), eq(planId)))
                    .willThrow(new PlanNotFoundException(planId));

            mockMvc.perform(post("/api/subscription/checkout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planId + "\"}"))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("deve retornar 503 em pt-BR quando o provedor de pagamento está indisponível")
        void shouldReturn503WhenBillingProviderIsUnavailable() throws Exception {
            UUID planId = UUID.randomUUID();
            given(billingProvider.createCheckout(any(), eq(planId)))
                    .willThrow(new BillingProviderUnavailableException());

            mockMvc.perform(post("/api/subscription/checkout")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"planId\":\"" + planId + "\"}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.title").value("Pagamento indisponível"))
                    .andExpect(jsonPath("$.detail").value(
                            "O pagamento online está temporariamente indisponível. Tente novamente em alguns "
                                    + "instantes ou entre em contato com o suporte para ativar seu plano."));
        }
    }

    // -------------------------------------------------------------------------
    // POST /api/subscription/revenue-report
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("POST /api/subscription/revenue-report")
    class SubmitRevenueReport {

        @Test
        @DisplayName("deve retornar 201 com RevenueReportResponse ao submeter relatório válido")
        void shouldReturn201WithResponse() throws Exception {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .reportedRevenue(new BigDecimal("5000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            RevenueReportResponse response = RevenueReportResponse.builder()
                    .id(UUID.randomUUID())
                    .merchantId(merchantId)
                    .reportedRevenue(new BigDecimal("5000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .suggestedPlanName("Básico")
                    .createdAt(LocalDateTime.now())
                    .build();

            given(subscriptionService.submitRevenueReport(any(), any(RevenueReportRequest.class)))
                    .willReturn(response);

            mockMvc.perform(post("/api/subscription/revenue-report")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.suggestedPlanName").value("Básico"));
        }

        @Test
        @DisplayName("deve retornar 400 quando faturamento está ausente")
        void shouldReturn400WhenRevenueIsMissing() throws Exception {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            mockMvc.perform(post("/api/subscription/revenue-report")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("deve retornar 409 quando já existe relatório para o mês")
        void shouldReturn409WhenDuplicateReport() throws Exception {
            RevenueReportRequest request = RevenueReportRequest.builder()
                    .reportedRevenue(new BigDecimal("5000.00"))
                    .referenceMonth(LocalDate.of(2026, 5, 1))
                    .build();

            given(subscriptionService.submitRevenueReport(any(), any(RevenueReportRequest.class)))
                    .willThrow(new DuplicateRevenueReportException(LocalDate.of(2026, 5, 1)));

            mockMvc.perform(post("/api/subscription/revenue-report")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isConflict());
        }
    }
}
