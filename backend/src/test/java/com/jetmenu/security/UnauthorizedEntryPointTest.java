package com.jetmenu.security;

import com.jetmenu.auth.AuthHelper;
import com.jetmenu.config.SecurityConfig;
import com.jetmenu.ingredient.IngredientController;
import com.jetmenu.ingredient.IngredientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real {@link SecurityConfig} chain (not the default @WebMvcTest security)
 * to assert what an <b>unauthenticated</b> business request receives.
 * <p>
 * Reproduces a production bug: a POST that reaches the backend without a valid bearer token
 * (Supabase session gone/not refreshed) was answered with <b>403</b> by Spring Security's
 * default entry point. The SPA only signs out and redirects to /login on <b>401</b>, so the
 * user stayed on the page and saw a misleading error — which happened to be while creating a
 * duplicate ingredient, making it look like the 409 duplicate handler was returning 403.
 */
@WebMvcTest(IngredientController.class)
@Import(SecurityConfig.class)
@DisplayName("SecurityConfig — unauthenticated request")
class UnauthorizedEntryPointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IngredientService ingredientService;

    @MockitoBean
    private AuthHelper authHelper;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("POST sem token deve retornar 401 (não 403) com ProblemDetail em pt-BR")
    void unauthenticatedPost_shouldReturn401WithProblemDetail() throws Exception {
        mockMvc.perform(post("/api/ingredients")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Farinha\",\"unit\":\"kg\",\"costPerUnit\":1.0}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Autenticação necessária"));
    }
}
