package com.jetmenu.billing;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlanController.class)
@WithMockUser
@DisplayName("PlanController")
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SubscriptionService subscriptionService;

    private PlanResponse planResponse;

    @BeforeEach
    void setUp() {
        planResponse = PlanResponse.builder()
                .id(UUID.randomUUID())
                .name("Básico")
                .minRevenue(new BigDecimal("0.00"))
                .maxRevenue(new BigDecimal("10000.00"))
                .priceMonthly(new BigDecimal("99.00"))
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("GET /api/plans")
    class ListActivePlans {

        @Test
        @DisplayName("deve retornar 200 com lista de planos ativos")
        void shouldReturn200WithActivePlans() throws Exception {
            given(subscriptionService.listActivePlans()).willReturn(List.of(planResponse));

            mockMvc.perform(get("/api/plans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$[0].name").value("Básico"))
                    .andExpect(jsonPath("$[0].priceMonthly").value(99.0));
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há planos ativos")
        void shouldReturnEmptyListWhenNoActivePlans() throws Exception {
            given(subscriptionService.listActivePlans()).willReturn(List.of());

            mockMvc.perform(get("/api/plans"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray())
                    .andExpect(jsonPath("$").isEmpty());
        }
    }
}
