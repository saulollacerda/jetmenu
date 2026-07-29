package com.jetmenu.dashboard;

import com.jetmenu.auth.AuthHelper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@WithMockUser
@DisplayName("DashboardController")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AuthHelper authHelper;

    private DashboardResponse dashboardResponse;

    @BeforeEach
    void setUp() {
        given(authHelper.getMerchantId(any(Authentication.class))).willReturn(UUID.randomUUID());
        dashboardResponse = DashboardResponse.builder()
                .totalSales(new BigDecimal("1500.00"))
                .orderCount(10L)
                .averageTicket(new BigDecimal("150.00"))
                .estimatedProfit(new BigDecimal("500.00"))
                .averageMarginPct(new BigDecimal("58.00"))
                .salesByDay(List.of(
                        DailySales.builder()
                                .date(LocalDate.of(2026, 3, 1))
                                .total(new BigDecimal("800.00"))
                                .build(),
                        DailySales.builder()
                                .date(LocalDate.of(2026, 3, 2))
                                .total(new BigDecimal("700.00"))
                                .build()
                ))
                .topProducts(List.of(
                        TopProduct.builder()
                                .productName("X-Burguer")
                                .quantitySold(25L)
                                .build(),
                        TopProduct.builder()
                                .productName("Pizza Margherita")
                                .quantitySold(18L)
                                .build()
                ))
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/dashboard")
    class GetDashboard {

        @Test
        @DisplayName("deve retornar 200 com DashboardResponse com filtro de data")
        void shouldReturn200WithDashboardResponseWithDateFilter() throws Exception {
            given(dashboardService.getDashboard(any(), eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                    .willReturn(dashboardResponse);

            mockMvc.perform(get("/api/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSales").value(1500.00))
                    .andExpect(jsonPath("$.orderCount").value(10))
                    .andExpect(jsonPath("$.averageTicket").value(150.00))
                    .andExpect(jsonPath("$.estimatedProfit").value(500.00))
                    .andExpect(jsonPath("$.averageMarginPct").value(58.00));
        }

        @Test
        @DisplayName("deve retornar 200 com salesByDay no response")
        void shouldReturn200WithSalesByDay() throws Exception {
            given(dashboardService.getDashboard(any(), any(), any())).willReturn(dashboardResponse);

            mockMvc.perform(get("/api/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.salesByDay").isArray())
                    .andExpect(jsonPath("$.salesByDay[0].date").value("2026-03-01"))
                    .andExpect(jsonPath("$.salesByDay[0].total").value(800.00))
                    .andExpect(jsonPath("$.salesByDay[1].date").value("2026-03-02"))
                    .andExpect(jsonPath("$.salesByDay[1].total").value(700.00));
        }

        @Test
        @DisplayName("deve retornar 200 com topProducts no response")
        void shouldReturn200WithTopProducts() throws Exception {
            given(dashboardService.getDashboard(any(), any(), any())).willReturn(dashboardResponse);

            mockMvc.perform(get("/api/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.topProducts").isArray())
                    .andExpect(jsonPath("$.topProducts[0].productName").value("X-Burguer"))
                    .andExpect(jsonPath("$.topProducts[0].quantitySold").value(25))
                    .andExpect(jsonPath("$.topProducts[1].productName").value("Pizza Margherita"))
                    .andExpect(jsonPath("$.topProducts[1].quantitySold").value(18));
        }

        @Test
        @DisplayName("deve retornar 200 sem parâmetros de data (usa padrão hoje)")
        void shouldReturn200WithoutDateParams() throws Exception {
            given(dashboardService.getDashboard(any(), isNull(), isNull())).willReturn(dashboardResponse);

            mockMvc.perform(get("/api/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSales").value(1500.00));
        }

        @Test
        @DisplayName("deve retornar 200 com KPIs zerados quando não há dados")
        void shouldReturn200WithZeroKpis() throws Exception {
            DashboardResponse emptyResponse = DashboardResponse.builder()
                    .totalSales(BigDecimal.ZERO)
                    .orderCount(0L)
                    .averageTicket(BigDecimal.ZERO)
                    .estimatedProfit(BigDecimal.ZERO)
                    .salesByDay(List.of())
                    .topProducts(List.of())
                    .build();

            given(dashboardService.getDashboard(any(), any(), any())).willReturn(emptyResponse);

            mockMvc.perform(get("/api/dashboard")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSales").value(0))
                    .andExpect(jsonPath("$.orderCount").value(0))
                    .andExpect(jsonPath("$.averageTicket").value(0))
                    .andExpect(jsonPath("$.estimatedProfit").value(0))
                    .andExpect(jsonPath("$.salesByDay").isEmpty())
                    .andExpect(jsonPath("$.topProducts").isEmpty());
        }

        @Test
        @DisplayName("deve retornar 200 com apenas startDate (endDate nulo)")
        void shouldReturn200WithOnlyStartDate() throws Exception {
            given(dashboardService.getDashboard(any(), eq(LocalDate.of(2026, 3, 1)), isNull()))
                    .willReturn(dashboardResponse);

            mockMvc.perform(get("/api/dashboard")
                            .param("startDate", "2026-03-01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalSales").value(1500.00));
        }
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/ingredient-ranking
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("GET /api/dashboard/ingredient-ranking")
    class IngredientRanking {

        @Test
        @DisplayName("deve retornar 200 com array de ingredientes no contrato esperado")
        void shouldReturn200WithIngredientArray() throws Exception {
            given(dashboardService.ingredientRanking(any(),
                    eq(LocalDate.of(2026, 3, 1)), eq(LocalDate.of(2026, 3, 31))))
                    .willReturn(List.of(
                            IngredientConsumption.builder()
                                    .ingredientName("Queijo")
                                    .unit("g")
                                    .totalQuantity(new BigDecimal("100"))
                                    .totalCost(new BigDecimal("12.00"))
                                    .build(),
                            IngredientConsumption.builder()
                                    .ingredientName("Bacon")
                                    .unit("g")
                                    .totalQuantity(new BigDecimal("250"))
                                    .totalCost(new BigDecimal("10.00"))
                                    .build()
                    ));

            mockMvc.perform(get("/api/dashboard/ingredient-ranking")
                            .param("startDate", "2026-03-01")
                            .param("endDate", "2026-03-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].ingredientName").value("Queijo"))
                    .andExpect(jsonPath("$[0].unit").value("g"))
                    .andExpect(jsonPath("$[0].totalQuantity").value(100))
                    .andExpect(jsonPath("$[0].totalCost").value(12.00))
                    .andExpect(jsonPath("$[1].ingredientName").value("Bacon"))
                    .andExpect(jsonPath("$[1].totalQuantity").value(250))
                    .andExpect(jsonPath("$[1].totalCost").value(10.00));
        }

        @Test
        @DisplayName("deve retornar 200 sem parâmetros de data (usa padrão hoje)")
        void shouldReturn200WithoutDateParams() throws Exception {
            given(dashboardService.ingredientRanking(any(), isNull(), isNull()))
                    .willReturn(List.of());

            mockMvc.perform(get("/api/dashboard/ingredient-ranking"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }
}

